/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MontogramTextTools;
import org.telegram.messenger.MontogramTextWorkbench;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.MontogramMaterial;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

/** Explicit own-input tools. No chat resolver, persistence, clipboard reads or send APIs. */
public final class MontogramTextToolsActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {
    private static final int STATS = 1, INVISIBLE = 2, KEYBOARD = 3, PAIRS = 4, DUPLICATES = 5, SPLIT = 6, COMPARE = 7, URL = 8, SCRIPTS = 9;
    private boolean active, destroyed;
    private long self;
    private ToolDialog owned;

    public MontogramTextToolsActivity(int account) { setCurrentAccount(account); }
    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.activeAccountChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout);
        return true;
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.activeAccountChanged || id == NotificationCenter.appDidLogout) drop();
    }
    private static String text(int resource) { return LocaleController.getString(resource); }
    @Override public Theme.ResourcesProvider getResourceProvider() { return MontogramMaterial.scoped(super.getResourceProvider()); }
    @Override protected CharSequence getTitle() { return text(R.string.MontogramTextTools); }
    private boolean alive() {
        return active && !destroyed && !isPaused() && currentAccount == UserConfig.selectedAccount
                && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter
                && UserConfig.getInstance(currentAccount).isClientActivated() && self > 0
                && self == UserConfig.getInstance(currentAccount).getClientUserId()
                && getParentActivity() != null && !getParentActivity().isFinishing() && !getParentActivity().isDestroyed();
    }
    @Override public void onResume() {
        super.onResume(); active = true;
        self = UserConfig.getInstance(currentAccount).getClientUserId();
    }
    private void drop() { active = false; self = 0; if (owned != null) owned.clear(true); }
    @Override public void onPause() { drop(); super.onPause(); }
    @Override public void onFragmentDestroy() {
        destroyed = true;
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.activeAccountChanged);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.appDidLogout);
        drop(); super.onFragmentDestroy();
    }
    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asShadow(text(R.string.MontogramTextToolsInfo)));
        items.add(UItem.asButton(STATS, R.drawable.settings_chat, text(R.string.MontogramTextStatistics)));
        items.add(UItem.asButton(INVISIBLE, R.drawable.settings_chat, text(R.string.MontogramInvisibleInspector)));
        items.add(UItem.asButton(KEYBOARD, R.drawable.settings_chat, text(R.string.MontogramKeyboardRepair)));
        items.add(UItem.asButton(PAIRS, R.drawable.settings_chat, text(R.string.MontogramPairsInspector)));
        items.add(UItem.asButton(DUPLICATES, R.drawable.settings_chat, text(R.string.MontogramDuplicateLines)));
        items.add(UItem.asButton(SPLIT, R.drawable.settings_chat, text(R.string.MontogramSplitPreview)));
        items.add(UItem.asButton(COMPARE, R.drawable.settings_chat, text(R.string.MontogramCompareTexts)));
        items.add(UItem.asButton(URL, R.drawable.settings_chat, text(R.string.MontogramUrlStructure)));
        items.add(UItem.asButton(SCRIPTS, R.drawable.settings_chat, text(R.string.MontogramMixedScripts)));
    }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        if (!alive()) return;
        if (item.id == STATS || item.id == INVISIBLE || item.id == PAIRS || item.id == DUPLICATES || item.id == SPLIT || item.id == URL || item.id == SCRIPTS) openInput(item.id, true);
        else if (item.id == COMPARE) openComparison();
        else if (item.id == KEYBOARD) {
            showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                    .setTitle(text(R.string.MontogramKeyboardRepair))
                    .setItems(new CharSequence[]{"EN → RU", "RU → EN"}, (dialog, which) -> {
                        if (alive() && (which == 0 || which == 1)) openInput(KEYBOARD, which == 0);
                    }).create());
        }
    }
    private int title(int tool) {
        switch (tool) {
            case STATS: return R.string.MontogramTextStatistics;
            case INVISIBLE: return R.string.MontogramInvisibleInspector;
            case KEYBOARD: return R.string.MontogramKeyboardRepair;
            case PAIRS: return R.string.MontogramPairsInspector;
            case DUPLICATES: return R.string.MontogramDuplicateLines;
            case SPLIT: return R.string.MontogramSplitPreview;
            case COMPARE: return R.string.MontogramCompareTexts;
            case URL: return R.string.MontogramUrlStructure;
            case SCRIPTS: return R.string.MontogramMixedScripts;
            default: throw new IllegalArgumentException("Unknown text tool");
        }
    }

    // Local window boundary only. Losing focus is a privacy fallback, not a lock detector.
    private static final class DialogPrivacyState {
        boolean focused, cleared;
        boolean lostFocus(boolean hasFocus) {
            if (cleared) return false;
            if (hasFocus) { focused = true; return false; }
            return focused;
        }
        boolean clear() { if (cleared) return false; cleared = true; return true; }
    }
    private final class ToolDialog {
        final DialogPrivacyState state = new DialogPrivacyState();
        AlertDialog dialog;
        TextView field, secondField;
        ArrayList<String> parts;
        View focusView;
        android.view.ViewTreeObserver.OnWindowFocusChangeListener focusListener;
        String payload;
        boolean current() { return owned == this && !state.cleared && alive(); }
        void focus(boolean focused) { if (state.lostFocus(focused)) clear(true); }
        void clear(boolean dismiss) {
            if (!state.clear()) return;
            if (field != null) field.setText("");
            if (secondField != null) secondField.setText("");
            secondField = null;
            if (parts != null) parts.clear(); parts = null;
            if (focusView != null && focusListener != null && focusView.getViewTreeObserver().isAlive())
                focusView.getViewTreeObserver().removeOnWindowFocusChangeListener(focusListener);
            focusView = null; focusListener = null;
            payload = null; field = null;
            AlertDialog previous = dialog; dialog = null;
            if (owned == this) owned = null;
            // Do not dismiss synchronously during Android window-focus dispatch.
            if (dismiss && previous != null) AndroidUtilities.runOnUIThread(() -> previous.dismiss());
        }
    }
    private void openInput(int tool, boolean englishToRussian) {
        if (!alive()) return;
        if (owned != null) owned.clear(true);
        final ToolDialog session = new ToolDialog(); owned = session;
        EditText input = new EditText(getParentActivity()) {
            @Override public void onWindowFocusChanged(boolean focused) {
                super.onWindowFocusChanged(focused); session.focus(focused);
            }
        };
        session.field = input;
        input.setSaveEnabled(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setMinLines(3); input.setMaxLines(10);
        input.setHint(text(R.string.MontogramTextToolInput));
        input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramTextTools.MAX_UTF16)});
        session.dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(title(tool))).setMessage(text(tool == PAIRS ? R.string.MontogramPairsInfo
                        : tool == DUPLICATES ? R.string.MontogramDuplicateInfo : R.string.MontogramTextToolsInfo)).setView(input)
                .setNegativeButton(text(R.string.Cancel), (dialog, button) -> session.clear(true))
                .setPositiveButton(text(R.string.MontogramLinkPreview), (dialog, button) -> {
                    if (!session.current()) { session.clear(true); return; }
                    String original = input.getText().toString();
                    String result, summary = null;
                    try {
                        if (tool == SPLIT) {
                            ArrayList<String> parts = MontogramTextWorkbench.split(original,
                                    Math.min(MontogramTextTools.MAX_UTF16, MessagesController.getInstance(currentAccount).getMaxMessageLength()));
                            session.clear(true); openParts(parts); return;
                        }
                        if (tool == DUPLICATES) {
                            MontogramTextTools.LineDeduplication dedup = MontogramTextTools.deduplicateLines(original);
                            result = dedup.text; summary = duplicateSummary(dedup);
                        } else result = result(tool, original, englishToRussian);
                    }
                    catch (IllegalArgumentException invalid) { session.clear(true); showError(); return; }
                    if (!session.current()) { session.clear(true); return; }
                    if (result.length() > MontogramTextWorkbench.MAX_OUTPUT_UTF16) { session.clear(true); showError(); return; }
                    session.clear(true);
                    openResult(tool, result, summary);
                }).create();
        showOwned(session);
    }
    private String result(int tool, String original, boolean englishToRussian) {
        if (tool == STATS) {
            MontogramTextTools.Stats stats = MontogramTextTools.statistics(original);
            return String.format(Locale.getDefault(), text(R.string.MontogramTextStatisticsResult),
                    stats.utf16, stats.codePoints, stats.utf8Bytes, stats.lines, stats.tokenRuns);
        }
        if (tool == INVISIBLE) {
            MontogramTextTools.Inspection inspection = MontogramTextTools.inspectInvisible(original);
            StringBuilder result = new StringBuilder(text(R.string.MontogramInvisibleInfo));
            for (int i = 0; i < inspection.positions.length; i++) result.append(String.format(Locale.ROOT,
                    "\n#%d  U+%04X", inspection.positions[i], inspection.codePoints[i]));
            if (inspection.positions.length == 0) result.append('\n').append(text(R.string.MontogramInvisibleNone));
            if (inspection.omitted > 0) result.append('\n').append(String.format(Locale.getDefault(),
                    text(R.string.MontogramInvisibleOmitted), inspection.omitted));
            return result.toString();
        }
        if (tool == PAIRS) {
            MontogramTextTools.PairInspection pairs = MontogramTextTools.inspectPairs(original);
            StringBuilder report = new StringBuilder(text(R.string.MontogramPairsInfo));
            for (int i = 0; i < pairs.positions.length; i++) {
                int label = pairs.kinds[i] == MontogramTextTools.UNCLOSED ? R.string.MontogramPairUnclosed
                        : pairs.expectedClosers[i] == 0 ? R.string.MontogramPairNoOpener : R.string.MontogramPairMismatch;
                report.append('\n').append(String.format(Locale.getDefault(), text(label),
                        pairs.positions[i], pairs.codePoints[i], pairs.expectedClosers[i]));
            }
            if (pairs.positions.length == 0) report.append('\n').append(text(R.string.MontogramPairsNone));
            if (pairs.omitted > 0) report.append('\n').append(String.format(Locale.getDefault(),
                    text(R.string.MontogramInvisibleOmitted), pairs.omitted));
            return report.toString();
        }
        if (tool == URL) {
            MontogramTextWorkbench.UrlParts url = MontogramTextWorkbench.inspectUrl(original);
            StringBuilder report = new StringBuilder(text(R.string.MontogramUrlStructureInfo));
            report.append(String.format(Locale.getDefault(), text(R.string.MontogramUrlStructureResult),
                    url.scheme, url.host, url.port < 0 ? "—" : Integer.toString(url.port), url.path, url.parameterCount));
            for (int i=0;i<url.parameterNames.length;i++) report.append("\n").append(i+1).append(": ").append(url.parameterNames[i]).append(" = […]");
            if(url.parameterCount>url.parameterNames.length) report.append("\n").append(String.format(Locale.getDefault(),text(R.string.MontogramInvisibleOmitted),url.parameterCount-url.parameterNames.length));
            return report.toString();
        }
        if (tool == SCRIPTS) {
            MontogramTextWorkbench.ScriptInspection inspection = MontogramTextWorkbench.inspectScripts(original);
            StringBuilder report = new StringBuilder(text(R.string.MontogramMixedScriptsInfo));
            for (int i=0;i<inspection.firstPositions.length;i++) report.append(String.format(Locale.getDefault(),
                    text(R.string.MontogramMixedScriptsRow), inspection.firstPositions[i], inspection.lastPositions[i],
                    ((inspection.scripts[i]&1)!=0?" LAT":"")+((inspection.scripts[i]&2)!=0?" CYR":"")+((inspection.scripts[i]&4)!=0?" GR":"")));
            if(inspection.firstPositions.length==0) report.append('\n').append(text(R.string.MontogramPairsNone));
            if(inspection.omitted>0) report.append('\n').append(String.format(Locale.getDefault(),text(R.string.MontogramInvisibleOmitted),inspection.omitted));
            return report.toString();
        }
        if (tool == KEYBOARD) return MontogramTextTools.convertKeyboard(original, englishToRussian);
        throw new IllegalArgumentException("Unknown text tool");
    }
    private String duplicateSummary(MontogramTextTools.LineDeduplication dedup) {
        StringBuilder summary = new StringBuilder(String.format(Locale.getDefault(),
                text(R.string.MontogramDuplicateSummary), dedup.removed));
        if (dedup.removedLines.length > 0) {
            summary.append('\n').append(text(R.string.MontogramDuplicateRows));
            for (int i = 0; i < dedup.removedLines.length; i++) summary.append(i == 0 ? " " : ", ").append(dedup.removedLines[i]);
        }
        if (dedup.omitted > 0) summary.append('\n').append(String.format(Locale.getDefault(),
                text(R.string.MontogramInvisibleOmitted), dedup.omitted));
        return summary.toString();
    }
    private void openResult(int tool, String result, String summary) {
        if (!alive()) return;
        final ToolDialog session = new ToolDialog(); owned = session; session.payload = result;
        TextView output = new TextView(getParentActivity()) {
            @Override public void onWindowFocusChanged(boolean focused) {
                super.onWindowFocusChanged(focused); session.focus(focused);
            }
        };
        session.field = output;
        output.setSaveEnabled(false); output.setTextSize(16);
        output.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        output.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        output.setText(result);
        android.widget.ScrollView scroll = new android.widget.ScrollView(getParentActivity());
        scroll.setSaveEnabled(false); scroll.addView(output);
        session.dialog = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(text(title(tool))).setMessage(summary).setView(scroll)
                .setNegativeButton(text(R.string.Cancel), (dialog, button) -> session.clear(true))
                .setPositiveButton(text(R.string.Copy), (dialog, button) -> {
                    if (session.current() && session.payload != null) {
                        boolean copied = AndroidUtilities.addToClipboard(session.payload);
                        session.clear(true);
                        if (!copied) showError();
                    } else session.clear(true);
                }).create();
        showOwned(session);
    }
    private void showOwned(ToolDialog session) {
        session.dialog.setOnShowListener(dialog -> {
            if (!session.current()) { session.clear(true); return; }
            session.focusView = session.dialog.getWindow().getDecorView();
            session.focusListener = focused -> session.focus(focused);
            session.focusView.getViewTreeObserver().addOnWindowFocusChangeListener(session.focusListener);
            session.focus(session.focusView.hasWindowFocus());
        });
        if (showDialog(session.dialog, dialog -> session.clear(false)) == null) session.clear(true);
    }
    private void openParts(ArrayList<String> parts) {
        if (!alive()) { parts.clear(); return; }
        if (parts.isEmpty()) { openResult(SPLIT,"",text(R.string.MontogramSplitInfo)); return; }
        final ToolDialog session = new ToolDialog(); owned=session; session.parts=parts;
        CharSequence[] labels = new CharSequence[parts.size()];
        for(int i=0;i<labels.length;i++) labels[i]=String.format(Locale.getDefault(),text(R.string.MontogramSplitPart),i+1,parts.size(),parts.get(i).length());
        session.dialog=new AlertDialog.Builder(getParentActivity(),getResourceProvider())
                .setTitle(text(R.string.MontogramSplitPreview)).setMessage(text(R.string.MontogramSplitInfo))
                .setItems(labels,(dialog,index)->{
                    if(!session.current()||index<0||index>=session.parts.size()){session.clear(true);return;}
                    String piece=session.parts.get(index);String summary=labels[index].toString();session.clear(true);
                    openResult(SPLIT,piece,summary);
                }).setNegativeButton(text(R.string.Cancel),(dialog,button)->session.clear(true)).create();
        showOwned(session);
    }
    private EditText comparisonInput(ToolDialog session,int hint) {
        EditText field=new EditText(getParentActivity());field.setSaveEnabled(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        field.setMinLines(2);field.setMaxLines(5);field.setHint(text(hint));field.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramTextTools.MAX_UTF16)});
        return field;
    }
    private void openComparison() {
        if(!alive())return;
        final ToolDialog session=new ToolDialog();owned=session;
        android.widget.LinearLayout form=new android.widget.LinearLayout(getParentActivity());form.setOrientation(android.widget.LinearLayout.VERTICAL);form.setSaveEnabled(false);
        form.setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(8),AndroidUtilities.dp(24),AndroidUtilities.dp(8));
        EditText before=comparisonInput(session,R.string.MontogramCompareBefore),after=comparisonInput(session,R.string.MontogramCompareAfter);
        session.field=before;session.secondField=after;form.addView(before);form.addView(after);
        session.dialog=new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramCompareTexts))
                .setMessage(text(R.string.MontogramCompareInfo)).setView(form)
                .setNegativeButton(text(R.string.Cancel),(dialog,button)->session.clear(true))
                .setPositiveButton(text(R.string.MontogramLinkPreview),(dialog,button)->{
                    if(!session.current()){session.clear(true);return;}
                    try {
                        MontogramTextWorkbench.Comparison diff=MontogramTextWorkbench.compare(before.getText().toString(),after.getText().toString());
                        String result=String.format(Locale.getDefault(),text(R.string.MontogramCompareResult),diff.commonPrefixPoints,diff.commonSuffixPoints,diff.removed,diff.inserted);
                        if(result.length()>MontogramTextWorkbench.MAX_OUTPUT_UTF16)throw new IllegalArgumentException("Output limit");
                        session.clear(true);openResult(COMPARE,result,text(R.string.MontogramCompareInfo));
                    }catch(IllegalArgumentException invalid){session.clear(true);showError();}
                }).create();showOwned(session);
    }
    private void showError() {
        if (!alive()) return;
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(getTitle())
                .setMessage(text(R.string.MontogramTextToolInvalid)).setPositiveButton(text(R.string.OK), null).create());
    }
}
