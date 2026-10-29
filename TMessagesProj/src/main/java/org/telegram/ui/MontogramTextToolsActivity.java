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
    private static final int STATS = 1, INVISIBLE = 2, KEYBOARD = 3, PAIRS = 4, DUPLICATES = 5;
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
    }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        if (!alive()) return;
        if (item.id == STATS || item.id == INVISIBLE || item.id == PAIRS || item.id == DUPLICATES) openInput(item.id, true);
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
        TextView field;
        String payload;
        boolean current() { return owned == this && !state.cleared && alive(); }
        void focus(boolean focused) { if (state.lostFocus(focused)) clear(true); }
        void clear(boolean dismiss) {
            if (!state.clear()) return;
            if (field != null) field.setText("");
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
                        if (tool == DUPLICATES) {
                            MontogramTextTools.LineDeduplication dedup = MontogramTextTools.deduplicateLines(original);
                            result = dedup.text; summary = duplicateSummary(dedup);
                        } else result = result(tool, original, englishToRussian);
                    }
                    catch (IllegalArgumentException invalid) { session.clear(true); showError(); return; }
                    if (!session.current()) { session.clear(true); return; }
                    session.clear(true);
                    openResult(tool, result, summary);
                }).create();
        if (showDialog(session.dialog, dialog -> session.clear(false)) == null) session.clear(true);
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
        if (showDialog(session.dialog, dialog -> session.clear(false)) == null) session.clear(true);
    }
    private void showError() {
        if (!alive()) return;
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(getTitle())
                .setMessage(text(R.string.MontogramTextToolInvalid)).setPositiveButton(text(R.string.OK), null).create());
    }
}
