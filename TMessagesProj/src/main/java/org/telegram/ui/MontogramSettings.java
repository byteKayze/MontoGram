/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;

import android.view.View;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.widget.EditText;
import org.telegram.messenger.MontogramSettingsIndex;
import org.telegram.messenger.MontogramSettingsIndex.Category;
import org.telegram.messenger.MontogramSettingsIndex.Entry;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import java.lang.ref.WeakReference;
import org.telegram.messenger.MontogramBackupIO;
import org.telegram.messenger.MontogramSettingsChange;
import org.telegram.messenger.MontogramPreferences.Key;
import org.telegram.messenger.MontogramPreferences.State;
import org.telegram.messenger.MontogramSettingsBackup;
import android.content.Context;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MontogramConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.ActionBar.MontogramMaterial;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

/** Optional settings using Telegram's own cells, navigation and theme preview. */
public final class MontogramSettings extends UniversalFragment {
    public static final int LIGHT = 1, DARK = 2, THEMES = 3, PERFORMANCE = 4, DATA = 5, STOCK_MODE = 6, MATERIAL_MODE = 7, MESSAGE_DETAILS = 8, BACKUP_EXPORT = 9, BACKUP_IMPORT = 10;
    private static final int SERVICES_INFO = 11, RESET_SETTINGS = 12, AVATAR_CIRCLE = 13, AVATAR_SQUIRCLE = 14, AVATAR_SQUARE = 15, HIDE_STORIES = 16, PROFILE_IDS = 17, DRAFT_COUNTER = 18, MESSAGE_SECONDS = 19, LINK_CLEANER = 20, CONFIRM_CALLS = 21, CONFIRM_STICKERS = 22, DENSITY_NATIVE = 23, DENSITY_COMPACT = 24, DENSITY_COMFORTABLE = 25, HAPTICS_NATIVE = 26, HAPTICS_OFF = 27, HAPTICS_REDUCED = 28, MENU_NATIVE = 29, MENU_FIRST = 30, MENU_LAST = 31, MENU_HIDE_TRANSLATE = 32, MENU_HIDE_SHARE = 33, MENU_HIDE_SAVE = 34, VOICE_PREVIEW = 35, HIDE_TYPING = 36, QUICK_REPLIES = 37, BOOKMARKS = 38, NOTES = 39, SEARCH_RESULT_OFFSET = 3000;
    private static final int EXPORT_DOCUMENT = 7401, IMPORT_DOCUMENT = 7402;
    private static final int TEXT_TOOLS = 40, SUPPORT_REPORT = 43, LOCAL_DATA = 44, READER = 45;
    private static final int MOTION_STANDARD = 41, MOTION_OFF = 42;
    private int linkCleanerGeneration;
    private final Category category;
    private final int targetSettingId;
    private MontogramSettingsIndex settingsIndex;
    private boolean searching;
    private String searchQuery = "";
    private int targetPosition = -1;

    public MontogramSettings() { this(null, 0); }
    private MontogramSettings(Category category, int targetSettingId) {
        this.category = category;
        this.targetSettingId = targetSettingId;
    }
    private boolean backupBusy, backupDestroyed;
    private int transferGeneration, changeGeneration;
    private MontogramBackupIO.Handle activeTransfer;
    private MontogramSettingsBackup.Snapshot exportSnapshot;
    private long transferRevision;
    private PendingTransfer pendingTransfer;
    private static final class PendingTransfer {
        final int generation;
        final long revision;
        final boolean exporting;
        final MontogramBackupIO.Status status;
        final MontogramSettingsBackup.Snapshot value;
        PendingTransfer(int generation, long revision, boolean exporting, MontogramBackupIO.Status status,
                MontogramSettingsBackup.Snapshot value) {
            this.generation = generation; this.revision = revision; this.exporting = exporting;
            this.status = status; this.value = value;
        }
    }

    @Override
    public Theme.ResourcesProvider getResourceProvider() {
        return MontogramMaterial.scoped(super.getResourceProvider());
    }

    @Override public View createView(Context context) {
        settingsIndex = createSettingsIndex();
        searching = false;
        searchQuery = "";
        View result = super.createView(context);
        actionBar.applyMontogramMaterial3(true);
        result.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        ActionBarMenuItem searchItem = actionBar.createMenu().addItem(0, R.drawable.outline_header_search)
            .setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                @Override public void onSearchExpand() { searching = true; refreshSearch(); }
                @Override public void onSearchCollapse() { searching = false; searchQuery = ""; refreshSearch(); }
                @Override public void onTextChanged(EditText editText) {
                    searchQuery = editText.getText().toString();
                    refreshSearch();
                }
            });
        searchItem.setSearchFieldHint(text(R.string.MontogramSearchSettings));
        searchItem.getSearchField().setFilters(new InputFilter[] {MontogramSettingsIndex::filterInsertion});
        return result;
    }

    private Runnable pendingAppearance;

    private MontogramSettingsChange changeTo(boolean material, boolean details) {
        State current=MontogramConfig.snapshot();
        return new MontogramSettingsChange(current,MontogramConfig.revision(),
            current.with(Key.MATERIAL3,material).with(Key.MESSAGE_DETAILS,details));
    }

    private void changeAppearance(boolean enabled) {
        if (enabled == MontogramConfig.isMaterial3()) return;
        applyChange(changeTo(enabled, MontogramConfig.showMessageDetails()), ++changeGeneration);
    }

    private void applyChange(MontogramSettingsChange change, int generation) {
        if (pendingAppearance != null || backupDestroyed) return;
        pendingAppearance = new Runnable() {
            @Override public void run() {
                if (backupDestroyed || generation != changeGeneration || getParentLayout() == null
                        || fragmentView == null || isPaused()) {
                    pendingAppearance = null;
                    return;
                }
                if (getParentLayout().isTransitionAnimationInProgress()
                        || getParentLayout().isSwipeInProgress() || getVisibleDialog() != null) {
                    AndroidUtilities.runOnUIThread(this, 100);
                    return;
                }
                pendingAppearance = null;
                MontogramSettingsChange.Result result = change.apply(MontogramConfig.snapshot(),
                    MontogramConfig.revision(), MontogramConfig::apply);
                if (result == MontogramSettingsChange.Result.STALE) {
                    showBackupMessage(R.string.MontogramSettingsStale);
                } else if (result == MontogramSettingsChange.Result.SAVE_FAILED) {
                    showBackupMessage(R.string.MontogramFeatureSaveError);
                } else if (result == MontogramSettingsChange.Result.APPLIED
                        && change.requiresLayoutReload()) {
                    NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.reloadInterface, MontogramConfig.APPEARANCE_RELOAD);
                } else if (listView != null) listView.adapter.update(true);
            }
        };
        AndroidUtilities.runOnUIThread(pendingAppearance);
    }

    private void confirmChange(MontogramSettingsChange change, int generation, int title) {
        if (backupDestroyed || generation != changeGeneration || getParentActivity() == null || isPaused()) return;
        if (change.changedFields.isEmpty()) { showBackupMessage(R.string.MontogramSettingsUnchanged); return; }
        StringBuilder summary = new StringBuilder();
        for(Key key:change.changedFields){
            if(summary.length()>0)summary.append('\n');
            summary.append(settingTitle(key)).append(": ").append(settingValue(key,change.previous))
                .append(" → ").append(settingValue(key,change.target));
        }
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(text(title))
            .setMessage(summary).setNegativeButton(text(R.string.Cancel), null)
            .setPositiveButton(text(R.string.ApplyTheme), (dialog, which) -> {
                if (!backupDestroyed && generation == changeGeneration && getParentActivity() != null && !isPaused())
                    applyChange(change, generation); // Revision/expected values rechecked when deferred commit actually runs.
            }).create());
    }

    private boolean linkCleanerAlive(int generation) {
        Activity owner=getParentActivity();
        return !backupDestroyed&&generation==linkCleanerGeneration&&!isPaused()&&owner!=null
            &&!owner.isFinishing()&&!owner.isDestroyed();
    }
    private void showLinkCleaner() {
        final int generation=++linkCleanerGeneration;
        if(!linkCleanerAlive(generation))return;
        EditText input=new EditText(getParentActivity());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI
            |android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
            |android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack,getResourceProvider()));
        input.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint,getResourceProvider()));
        input.setHint(text(R.string.MontogramLinkHint));
        input.setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(8),AndroidUtilities.dp(24),AndroidUtilities.dp(8));
        input.setFilters(new InputFilter[]{(source,start,end,dest,dstart,dend)->
            org.telegram.messenger.MontogramLinkCleaner.fitsInsertion(dest.length(),dend-dstart,end-start)
                ?null:dest.subSequence(dstart,dend)});
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider())
            .setTitle(text(R.string.MontogramLinkCleaner)).setMessage(text(R.string.MontogramLinkCleanerInfo))
            .setView(input).setNegativeButton(text(R.string.Cancel),null)
            .setPositiveButton(text(R.string.MontogramLinkPreview),(dialog,which)->{
                if(!linkCleanerAlive(generation))return;
                org.telegram.messenger.MontogramLinkCleaner.Result result=
                    org.telegram.messenger.MontogramLinkCleaner.clean(input.getText().toString());
                if(result.status==org.telegram.messenger.MontogramLinkCleaner.Status.INVALID){
                    showLinkCleanerError(R.string.MontogramLinkInvalid);return;
                }
                boolean cleaned=result.status==org.telegram.messenger.MontogramLinkCleaner.Status.CLEANED;
                String explanation=text(cleaned?R.string.MontogramLinkChanged:
                    result.status==org.telegram.messenger.MontogramLinkCleaner.Status.PROTECTED
                        ?R.string.MontogramLinkProtected:R.string.MontogramLinkUnchanged);
                AlertDialog.Builder preview=new AlertDialog.Builder(getParentActivity(),getResourceProvider())
                    .setTitle(text(R.string.MontogramLinkPreview))
                    .setMessage(text(R.string.MontogramLinkOriginal)+"\n"+result.original+"\n\n"
                        +text(R.string.MontogramLinkResult)+"\n"+result.cleaned+"\n\n"+explanation);
                if(cleaned){
                    preview.setNegativeButton(text(R.string.Cancel),null)
                        .setPositiveButton(text(R.string.Copy),(confirm,button)->{
                            if(linkCleanerAlive(generation)&&!AndroidUtilities.addToClipboard(result.cleaned))
                                showLinkCleanerError(R.string.MontogramLinkCopyFailed);
                        });
                }else preview.setPositiveButton(text(R.string.OK),null);
                showDialog(preview.create());
            }).create());
    }
    private void showLinkCleanerError(int message) {
        if(backupDestroyed||getParentActivity()==null||isPaused())return;
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider())
            .setTitle(text(R.string.MontogramLinkCleaner)).setMessage(text(message))
            .setPositiveButton(text(R.string.OK),null).create());
    }

    private String settingTitle(Key key){
        switch(key){
            case MATERIAL3:return text(R.string.MontogramStyleMaterial);
            case MESSAGE_DETAILS:return text(R.string.MontogramMessageDetails);
            case READER:return text(R.string.MontogramReader);
            case VOICE_PREVIEW:return text(R.string.MontogramVoicePreview);
            case HIDE_TYPING:return text(R.string.MontogramHideTyping);
            case MENU_ORDER:return text(R.string.MontogramMenuOrder);
            case MENU_HIDE_TRANSLATE:return text(R.string.MontogramMenuHideTranslate);
            case MENU_HIDE_SHARE:return text(R.string.MontogramMenuHideShare);
            case MENU_HIDE_SAVE:return text(R.string.MontogramMenuHideSave);
            case HAPTICS:return text(R.string.MontogramHaptics);
            case MOTION:return text(R.string.MontogramMotionTitle);
            case DIALOG_DENSITY:return text(R.string.MontogramDialogDensity);
            case AVATAR_SHAPE:return text(R.string.MontogramAvatarShape);
            case HIDE_STORIES:return text(R.string.MontogramHideStories);
            case PROFILE_IDS:return text(R.string.MontogramProfileIdentifiers);
            case DRAFT_COUNTER:return text(R.string.MontogramDraftCounter);
            case MESSAGE_SECONDS:return text(R.string.MontogramMessageSeconds);
            case CONFIRM_CALLS:return text(R.string.MontogramConfirmCalls);
            case CONFIRM_STICKERS:return text(R.string.MontogramConfirmStickers);
            default:throw new IllegalArgumentException("Unknown UI setting");
        }
    }
    private String settingValue(Key key,State state){
        if(key==Key.MOTION)return text(state.integer(key)==1?R.string.MontogramMotionOff:R.string.MontogramMotionStandard);
        if(key==Key.MENU_ORDER){int order=state.integer(key);return text(order==1?R.string.MontogramMenuFirst:
            order==2?R.string.MontogramMenuLast:R.string.MontogramMenuNative);}
        if(key==Key.HAPTICS){int mode=state.integer(key);return text(mode==1?R.string.MontogramHapticsOff:
            mode==2?R.string.MontogramHapticsReduced:R.string.MontogramHapticsNative);}
        if(key==Key.DIALOG_DENSITY){int mode=state.integer(key);return text(mode==1?R.string.MontogramDensityCompact:
            mode==2?R.string.MontogramDensityComfortable:R.string.MontogramDensityNative);}
        if(key==Key.AVATAR_SHAPE){int shape=state.integer(key);return text(shape==0?R.string.MontogramAvatarCircle:
            shape==1?R.string.MontogramAvatarSquircle:R.string.MontogramAvatarSquare);}
        return text(state.bool(key)?R.string.MontogramBackupOn:R.string.MontogramBackupOff);
    }
    private void changeSetting(Key key,Object value){
        State current=MontogramConfig.snapshot();
        applyChange(new MontogramSettingsChange(current,MontogramConfig.revision(),current.with(key,value)),++changeGeneration);
    }

    @Override public void onResume() {
        super.onResume();
        AndroidUtilities.runOnUIThread(this::showPendingTransfer);
    }

    @Override protected void onDialogDismiss(Dialog dialog) {
        super.onDialogDismiss(dialog);
        AndroidUtilities.runOnUIThread(this::showPendingTransfer);
    }

    @Override public void onFragmentDestroy() {
        if (pendingAppearance != null) AndroidUtilities.cancelRunOnUIThread(pendingAppearance);
        pendingAppearance = null;
        backupDestroyed = true;
        transferGeneration++;
        changeGeneration++;
        if (activeTransfer != null) activeTransfer.cancel();
        activeTransfer = null;
        exportSnapshot = null;
        pendingTransfer = null;
        super.onFragmentDestroy();
    }

    @Override protected CharSequence getTitle() { return category == null ? "Montogram" : categoryTitle(category); }
    private String text(int resource) { return LocaleController.getString(resource); }

    private String categoryTitle(Category value) {
        switch (value) {
            case APPEARANCE: return text(R.string.MontogramAppearance);
            case CHAT_TOOLS: return text(R.string.MontogramChatTools);
            case BACKUP: return text(R.string.MontogramBackupTitle);
            case PERFORMANCE: return text(R.string.MontogramPerformanceCategory);
            case NAVIGATION: return text(R.string.MontogramNavigationCategory);
            default: return text(R.string.MontogramAboutCategory);
        }
    }

    private Entry descriptor(int id, Category value, int title, int info, String aliases) {
        return new Entry(id, value, text(title), text(info), categoryTitle(value), aliases);
    }

    private MontogramSettingsIndex createSettingsIndex() {
        ArrayList<Entry> rows = new ArrayList<>();
        rows.add(descriptor(STOCK_MODE, Category.APPEARANCE, R.string.MontogramStyleStock, R.string.MontogramStyleInfo, "stock telegram standard default"));
        rows.add(descriptor(MATERIAL_MODE, Category.APPEARANCE, R.string.MontogramStyleMaterial, R.string.MontogramStyleInfo, "material3 md3 material 3"));
        rows.add(descriptor(MOTION_STANDARD, Category.APPEARANCE, R.string.MontogramMotionStandard, R.string.MontogramMotionInfo, "md3 switch motion standard анимация движение переключатель"));
        rows.add(descriptor(MOTION_OFF, Category.APPEARANCE, R.string.MontogramMotionOff, R.string.MontogramMotionInfo, "md3 switch animation off отключить анимацию"));
        rows.add(descriptor(LIGHT, Category.APPEARANCE, R.string.MontogramMaterialLight, R.string.MontogramPaletteInfo, "light palette"));
        rows.add(descriptor(DARK, Category.APPEARANCE, R.string.MontogramMaterialDark, R.string.MontogramPaletteInfo, "dark palette"));
        rows.add(descriptor(THEMES, Category.APPEARANCE, R.string.SettingsChat, R.string.MontogramNativeControlsInfo, "themes chat settings"));
        rows.add(descriptor(DENSITY_NATIVE,Category.APPEARANCE,R.string.MontogramDensityNative,R.string.MontogramDialogDensityInfo,"density spacing native плотность отступы"));
        rows.add(descriptor(DENSITY_COMPACT,Category.APPEARANCE,R.string.MontogramDensityCompact,R.string.MontogramDialogDensityInfo,"density spacing compact плотность компактный"));
        rows.add(descriptor(DENSITY_COMFORTABLE,Category.APPEARANCE,R.string.MontogramDensityComfortable,R.string.MontogramDialogDensityInfo,"density spacing comfortable плотность просторный"));
        rows.add(descriptor(AVATAR_CIRCLE,Category.APPEARANCE,R.string.MontogramAvatarCircle,R.string.MontogramAvatarShapeInfo,"avatar circle shape"));
        rows.add(descriptor(AVATAR_SQUIRCLE,Category.APPEARANCE,R.string.MontogramAvatarSquircle,R.string.MontogramAvatarShapeInfo,"avatar squircle shape"));
        rows.add(descriptor(AVATAR_SQUARE,Category.APPEARANCE,R.string.MontogramAvatarSquare,R.string.MontogramAvatarShapeInfo,"avatar square shape"));
        rows.add(descriptor(HIDE_STORIES,Category.NAVIGATION,R.string.MontogramHideStories,R.string.MontogramHideStoriesInfo,"hide stories strip navigation"));
        rows.add(descriptor(PROFILE_IDS,Category.CHAT_TOOLS,R.string.MontogramProfileIdentifiers,R.string.MontogramProfileIdentifiersInfo,"profile identifiers user chat id профиль идентификатор айди пользователь чат"));
        rows.add(descriptor(DRAFT_COUNTER,Category.CHAT_TOOLS,R.string.MontogramDraftCounter,R.string.MontogramDraftCounterInfo,"draft text character count length limit длина символ счётчик черновик лимит"));
        rows.add(descriptor(MESSAGE_SECONDS,Category.CHAT_TOOLS,R.string.MontogramMessageSeconds,R.string.MontogramMessageSecondsInfo,"message time seconds timestamp время секунды сообщения"));
        rows.add(descriptor(LINK_CLEANER,Category.CHAT_TOOLS,R.string.MontogramLinkCleaner,R.string.MontogramLinkCleanerInfo,"clean link tracking url utm очистка ссылка трекинг"));
        rows.add(descriptor(TEXT_TOOLS, Category.CHAT_TOOLS, R.string.MontogramTextTools,
                R.string.MontogramTextToolsInfo, "unicode invisible text statistics keyboard раскладка статистика невидимые символы"));
        rows.add(descriptor(CONFIRM_CALLS,Category.CHAT_TOOLS,R.string.MontogramConfirmCalls,R.string.MontogramConfirmCallsInfo,"confirm personal outgoing voice video calls подтверждение звонок вызов"));
        rows.add(descriptor(CONFIRM_STICKERS,Category.CHAT_TOOLS,R.string.MontogramConfirmStickers,R.string.MontogramConfirmStickersInfo,"confirm chat sticker panel preview suggestions greetings подтверждение стикер панель подсказка приветствие"));
        rows.add(descriptor(READER,Category.CHAT_TOOLS,R.string.MontogramReader,R.string.MontogramReaderInfo,"reader local text search reading чтение текст поиск"));
        rows.add(descriptor(VOICE_PREVIEW,Category.CHAT_TOOLS,R.string.MontogramVoicePreview,R.string.MontogramVoicePreviewInfo,"voice audio recording preview confirm голосовые предпросмотр прослушивание"));
        rows.add(descriptor(NOTES,Category.CHAT_TOOLS,R.string.MontogramNotes,R.string.MontogramNotesMemoryInfo,"local chat notes заметки чат записи"));
        rows.add(descriptor(BOOKMARKS,Category.CHAT_TOOLS,R.string.MontogramBookmarks,R.string.MontogramBookmarksMemoryInfo,"local bookmarks saved references закладки сообщения ссылки"));
        rows.add(descriptor(QUICK_REPLIES,Category.CHAT_TOOLS,R.string.MontogramQuickReplies,R.string.MontogramQuickRepliesMemoryInfo,"local quick replies templates быстрые ответы шаблоны"));
        rows.add(descriptor(HIDE_TYPING,Category.CHAT_TOOLS,R.string.MontogramHideTyping,R.string.MontogramHideTypingInfo,"privacy hide outgoing typing text activity скрыть статус печатает набор текста приватность"));
        rows.add(descriptor(MENU_NATIVE,Category.CHAT_TOOLS,R.string.MontogramMenuNative,R.string.MontogramMenuInfo,"message menu native порядок меню"));
        rows.add(descriptor(MENU_FIRST,Category.CHAT_TOOLS,R.string.MontogramMenuFirst,R.string.MontogramMenuInfo,"message menu tools first действия вначале"));
        rows.add(descriptor(MENU_LAST,Category.CHAT_TOOLS,R.string.MontogramMenuLast,R.string.MontogramMenuInfo,"message menu tools last действия вконце"));
        rows.add(descriptor(MENU_HIDE_TRANSLATE,Category.CHAT_TOOLS,R.string.MontogramMenuHideTranslate,R.string.MontogramMenuInfo,"message menu hide translate скрыть перевод"));
        rows.add(descriptor(MENU_HIDE_SHARE,Category.CHAT_TOOLS,R.string.MontogramMenuHideShare,R.string.MontogramMenuInfo,"message menu hide share скрыть поделиться"));
        rows.add(descriptor(MENU_HIDE_SAVE,Category.CHAT_TOOLS,R.string.MontogramMenuHideSave,R.string.MontogramMenuInfo,"message menu hide save download gallery скрыть сохранить"));
        rows.add(descriptor(MESSAGE_DETAILS, Category.CHAT_TOOLS, R.string.MontogramMessageDetails, R.string.MontogramMessageDetailsSettingInfo, "message details metadata id"));
        rows.add(descriptor(BACKUP_EXPORT, Category.BACKUP, R.string.MontogramBackupExport, R.string.MontogramBackupInfo, "export backup"));
        rows.add(descriptor(BACKUP_IMPORT, Category.BACKUP, R.string.MontogramBackupImport, R.string.MontogramBackupInfo, "import restore backup"));
        rows.add(descriptor(RESET_SETTINGS, Category.BACKUP, R.string.MontogramResetSettings,
            R.string.MontogramResetSettingsInfo, "reset defaults restore"));
        rows.add(descriptor(HAPTICS_NATIVE,Category.PERFORMANCE,R.string.MontogramHapticsNative,R.string.MontogramHapticsInfo,"haptics vibration native тактильный вибрация"));
        rows.add(descriptor(HAPTICS_OFF,Category.PERFORMANCE,R.string.MontogramHapticsOff,R.string.MontogramHapticsInfo,"haptics vibration off отключить вибрацию"));
        rows.add(descriptor(HAPTICS_REDUCED,Category.PERFORMANCE,R.string.MontogramHapticsReduced,R.string.MontogramHapticsInfo,"haptics vibration reduced frequency реже вибрация"));
        rows.add(descriptor(PERFORMANCE, Category.PERFORMANCE, R.string.SettingsPowerSaving, R.string.MontogramNativeControlsInfo, "performance battery animation power saving"));
        rows.add(descriptor(DATA, Category.PERFORMANCE, R.string.SettingsData, R.string.MontogramNativeControlsInfo, "data traffic network"));
        rows.add(descriptor(SERVICES_INFO, Category.ABOUT, R.string.MontogramServices, R.string.MontogramServicesInfo, "google firebase services notifications"));
        rows.add(descriptor(SUPPORT_REPORT,Category.ABOUT,R.string.MontogramSupportReport,R.string.MontogramSupportReportInfo,"support build version abi report отчёт поддержка сборка"));
        rows.add(descriptor(LOCAL_DATA,Category.BACKUP,R.string.MontogramLocalData,R.string.MontogramLocalDataInfo,"local data export import templates notes bookmarks данные экспорт импорт заметки закладки"));
        return new MontogramSettingsIndex(rows);
    }

    private void refreshSearch() {
        if (!backupDestroyed && listView != null) {
            listView.adapter.update(false);
            listView.scrollToPosition(0);
        }
    }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        targetPosition = -1;
        if (settingsIndex == null) return;
        if (searching) {
            for (Entry entry : settingsIndex.search(searchQuery)) {
                // Results navigate to the setting; a search can never toggle or import anything.
                items.add(UItem.asButton(SEARCH_RESULT_OFFSET + entry.id, entry.title, entry.categoryLabel));
            }
            if (items.isEmpty()) items.add(UItem.asShadow(text(R.string.MontogramSearchNoResults)));
            return;
        }
        if (category == null) {
            items.add(UItem.asShadow(text(R.string.MontogramStockDefault)));
            for (Category value : Category.values()) {
                if (!settingsIndex.entries(value).isEmpty())
                    items.add(UItem.asButton(value.navigationId, R.drawable.settings_chat, categoryTitle(value)));
            }
            items.add(UItem.asShadow(text(R.string.MontogramIndependentInfo)));
            return;
        }
        items.add(UItem.asHeader(categoryTitle(category)));
        String lastDescription = "";
        for (Entry entry : settingsIndex.entries(category)) {
            if (entry.id == targetSettingId) targetPosition = items.size();
            if (entry.id == STOCK_MODE || entry.id == MATERIAL_MODE) {
                items.add(UItem.asRadio(entry.id, entry.title).setChecked(
                    entry.id == MATERIAL_MODE ? MontogramConfig.isMaterial3() : !MontogramConfig.isMaterial3()));
            } else if(entry.id>=MOTION_STANDARD&&entry.id<=MOTION_OFF){
                items.add(UItem.asRadio(entry.id,entry.title).setChecked(MontogramConfig.motionMode()==entry.id-MOTION_STANDARD));
            } else if(entry.id==READER){
                items.add(UItem.asCheck(entry.id,entry.title).setChecked(MontogramConfig.readerEnabled()));
            } else if(entry.id==VOICE_PREVIEW){
                items.add(UItem.asCheck(entry.id,entry.title).setChecked(MontogramConfig.voicePreview()));
            } else if(entry.id==HIDE_TYPING){
                items.add(UItem.asCheck(entry.id,entry.title).setChecked(MontogramConfig.hideTyping()));
            } else if(entry.id>=MENU_NATIVE&&entry.id<=MENU_LAST){
                items.add(UItem.asRadio(entry.id,entry.title).setChecked(MontogramConfig.snapshot().integer(Key.MENU_ORDER)==entry.id-MENU_NATIVE));
            } else if(entry.id>=MENU_HIDE_TRANSLATE&&entry.id<=MENU_HIDE_SAVE){
                Key key=entry.id==MENU_HIDE_TRANSLATE?Key.MENU_HIDE_TRANSLATE:entry.id==MENU_HIDE_SHARE?Key.MENU_HIDE_SHARE:Key.MENU_HIDE_SAVE;
                items.add(UItem.asCheck(entry.id,entry.title).setChecked(MontogramConfig.snapshot().bool(key)));
            } else if(entry.id>=HAPTICS_NATIVE&&entry.id<=HAPTICS_REDUCED){
                items.add(UItem.asRadio(entry.id,entry.title).setChecked(MontogramConfig.hapticsMode()==entry.id-HAPTICS_NATIVE));
            } else if(entry.id>=DENSITY_NATIVE&&entry.id<=DENSITY_COMFORTABLE){
                items.add(UItem.asRadio(entry.id,entry.title).setChecked(MontogramConfig.dialogDensity()==entry.id-DENSITY_NATIVE));
            } else if(entry.id>=AVATAR_CIRCLE&&entry.id<=AVATAR_SQUARE){
                items.add(UItem.asRadio(entry.id,entry.title).setChecked(MontogramConfig.avatarShape()==entry.id-AVATAR_CIRCLE));
            } else if(entry.id==HIDE_STORIES){
                items.add(UItem.asCheck(entry.id,entry.title).setChecked(MontogramConfig.hideStories()));
            } else if(entry.id==PROFILE_IDS||entry.id==DRAFT_COUNTER||entry.id==MESSAGE_SECONDS||entry.id==CONFIRM_CALLS||entry.id==CONFIRM_STICKERS){
                items.add(UItem.asCheck(entry.id,entry.title).setChecked(entry.id==PROFILE_IDS?MontogramConfig.profileIds():entry.id==DRAFT_COUNTER?MontogramConfig.draftCounter():entry.id==MESSAGE_SECONDS?MontogramConfig.messageSeconds():entry.id==CONFIRM_CALLS?MontogramConfig.confirmCalls():MontogramConfig.confirmStickers()));
            } else if (entry.id == MESSAGE_DETAILS) {
                items.add(UItem.asCheck(entry.id, entry.title).setChecked(MontogramConfig.showMessageDetails()));
            } else if (entry.id == SERVICES_INFO) {
                items.add(UItem.asHeader(entry.id, entry.title));
            } else {
                items.add(UItem.asButton(entry.id, R.drawable.settings_chat, entry.title));
            }
            if (!entry.description.equals(lastDescription)) items.add(UItem.asShadow(entry.description));
            lastDescription = entry.description;
        }
    }

    @Override public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) AndroidUtilities.runOnUIThread(this::showPendingTransfer);
        if (isOpen && listView != null && targetPosition >= 0 && !backupDestroyed) {
            listView.scrollToPosition(targetPosition);
            Entry entry = settingsIndex.find(targetSettingId);
            if (entry != null) listView.announceForAccessibility(entry.title);
        }
    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        if (backupBusy || pendingAppearance != null) return;
        Category requested = Category.fromNavigationId(item.id);
        if (requested != null) { presentFragment(new MontogramSettings(requested, 0)); return; }
        if (item.id >= SEARCH_RESULT_OFFSET) {
            Entry entry = settingsIndex.find(item.id - SEARCH_RESULT_OFFSET);
            if (entry != null) presentFragment(new MontogramSettings(entry.category, entry.id));
            return;
        }
        switch (item.id) {
            case MOTION_STANDARD:case MOTION_OFF:
                changeSetting(Key.MOTION,item.id-MOTION_STANDARD);break;
            case RESET_SETTINGS:
                confirmChange(new MontogramSettingsChange(MontogramConfig.snapshot(),MontogramConfig.revision(),State.defaults()),
                    ++changeGeneration, R.string.MontogramResetSettings);
                break;
            case READER:changeSetting(Key.READER,!MontogramConfig.readerEnabled());break;
            case VOICE_PREVIEW:changeSetting(Key.VOICE_PREVIEW,!MontogramConfig.voicePreview());break;
            case HIDE_TYPING:changeSetting(Key.HIDE_TYPING,!MontogramConfig.hideTyping());break;
            case MENU_NATIVE:case MENU_FIRST:case MENU_LAST:
                changeSetting(Key.MENU_ORDER,item.id-MENU_NATIVE);break;
            case MENU_HIDE_TRANSLATE:changeSetting(Key.MENU_HIDE_TRANSLATE,!MontogramConfig.snapshot().bool(Key.MENU_HIDE_TRANSLATE));break;
            case MENU_HIDE_SHARE:changeSetting(Key.MENU_HIDE_SHARE,!MontogramConfig.snapshot().bool(Key.MENU_HIDE_SHARE));break;
            case MENU_HIDE_SAVE:changeSetting(Key.MENU_HIDE_SAVE,!MontogramConfig.snapshot().bool(Key.MENU_HIDE_SAVE));break;
            case HAPTICS_NATIVE:case HAPTICS_OFF:case HAPTICS_REDUCED:
                changeSetting(Key.HAPTICS,item.id-HAPTICS_NATIVE);break;
            case DENSITY_NATIVE:case DENSITY_COMPACT:case DENSITY_COMFORTABLE:
                changeSetting(Key.DIALOG_DENSITY,item.id-DENSITY_NATIVE);break;
            case AVATAR_CIRCLE:case AVATAR_SQUIRCLE:case AVATAR_SQUARE:
                changeSetting(Key.AVATAR_SHAPE,item.id-AVATAR_CIRCLE);break;
            case HIDE_STORIES:changeSetting(Key.HIDE_STORIES,!MontogramConfig.hideStories());break;
            case PROFILE_IDS:changeSetting(Key.PROFILE_IDS,!MontogramConfig.profileIds());break;
            case DRAFT_COUNTER:changeSetting(Key.DRAFT_COUNTER,!MontogramConfig.draftCounter());break;
            case MESSAGE_SECONDS:changeSetting(Key.MESSAGE_SECONDS,!MontogramConfig.messageSeconds());break;
            case CONFIRM_CALLS:changeSetting(Key.CONFIRM_CALLS,!MontogramConfig.confirmCalls());break;
            case CONFIRM_STICKERS:changeSetting(Key.CONFIRM_STICKERS,!MontogramConfig.confirmStickers());break;
            case NOTES: presentFragment(new MontogramNotesActivity(currentAccount)); break;
            case TEXT_TOOLS: presentFragment(new MontogramTextToolsActivity(currentAccount)); break;
            case BOOKMARKS: presentFragment(new MontogramBookmarksActivity(currentAccount)); break;
            case QUICK_REPLIES: presentFragment(new MontogramQuickRepliesActivity(currentAccount)); break;
            case LOCAL_DATA: presentFragment(new MontogramLocalDataActivity(currentAccount));break;
            case SUPPORT_REPORT:
                String report=org.telegram.messenger.MontogramTextWorkbench.supportReport(org.telegram.messenger.BuildVars.BUILD_VERSION_STRING,android.os.Build.VERSION.SDK_INT,android.os.Build.SUPPORTED_ABIS);
                showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramSupportReport))
                        .setMessage(report).setNegativeButton(text(R.string.Cancel),null)
                        .setPositiveButton(text(R.string.Copy),(dialog,button)->AndroidUtilities.addToClipboard(report)).create());break;
            case LINK_CLEANER: showLinkCleaner(); break;
            case BACKUP_EXPORT: chooseBackupDocument(true); break;
            case BACKUP_IMPORT: chooseBackupDocument(false); break;
            case MESSAGE_DETAILS:
                if (MontogramConfig.setMessageDetails(!MontogramConfig.showMessageDetails())) {
                    if (listView != null) listView.adapter.update(true);
                } else {
                    new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle("Montogram")
                        .setMessage(text(R.string.MontogramFeatureSaveError))
                        .setPositiveButton(text(R.string.OK), null).show();
                }
                break;
            case STOCK_MODE: changeAppearance(false); break;
            case MATERIAL_MODE: changeAppearance(true); break;
            case LIGHT: previewMaterialPalette(false); break;
            case DARK: previewMaterialPalette(true); break;
            case THEMES: presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC)); break;
            case PERFORMANCE: presentFragment(new LiteModeSettingsActivity()); break;
            case DATA: presentFragment(new DataSettingsActivity()); break;
        }
    }

    private void chooseBackupDocument(boolean exporting) {
        if (getParentActivity() == null || backupDestroyed) return;
        if (!MontogramBackupIO.isAvailable()) { showBackupMessage(R.string.MontogramBackupUnavailable); return; }
        if (MontogramBackupIO.isBusy()) { showBackupMessage(R.string.MontogramBackupBusy); return; }
        Intent intent = new Intent(exporting ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        if (exporting) intent.putExtra(Intent.EXTRA_TITLE, "Montogram-appearance.json");
        exportSnapshot = exporting ? new MontogramSettingsBackup.Snapshot(MontogramConfig.snapshot()) : null;
        backupBusy = true;
        transferRevision = MontogramConfig.revision();
        transferGeneration++;
        try { startActivityForResult(intent, exporting ? EXPORT_DOCUMENT : IMPORT_DOCUMENT); }
        catch (Exception unavailable) {
            backupBusy = false;
            exportSnapshot = null;
            showBackupMessage(R.string.MontogramBackupError);
        }
    }

    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != EXPORT_DOCUMENT && requestCode != IMPORT_DOCUMENT) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }
        if (!backupBusy || backupDestroyed) return;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            transferGeneration++;
            backupBusy = false;
            exportSnapshot = null;
            return;
        }
        Uri uri = data.getData();
        // Only a SAF provider document; never accept file:// or another scheme.
        if (!"content".equals(uri.getScheme())) {
            backupBusy = false;
            exportSnapshot = null;
            showBackupMessage(R.string.MontogramBackupError);
            return;
        }
        MontogramSettingsBackup.Snapshot snapshot = exportSnapshot;
        exportSnapshot = null;
        activeTransfer = transferBackup(uri, requestCode == EXPORT_DOCUMENT, snapshot,
            new WeakReference<>(this), transferGeneration, transferRevision);
    }

    private static MontogramBackupIO.Handle transferBackup(Uri uri, boolean exporting, MontogramSettingsBackup.Snapshot snapshot,
            WeakReference<MontogramSettings> target, int generation, long revision) {
        return MontogramBackupIO.transfer(uri, exporting, snapshot, (status, result) -> {
            MontogramSettings owner = target.get();
            if (owner == null || owner.backupDestroyed || owner.transferGeneration != generation) return;
            // UI-thread state only: retain only validated own UI values, never a URI, stream, exception or Activity.
            owner.activeTransfer = null;
            owner.pendingTransfer = new PendingTransfer(generation, revision, exporting, status, result);
            owner.showPendingTransfer();
        });
    }

    private void showPendingTransfer() {
        if (pendingTransfer == null || backupDestroyed || fragmentView == null || isPaused() || getParentActivity() == null
                || getParentActivity().isFinishing() || getParentLayout() == null
                || getParentLayout().isTransitionAnimationInProgress() || getParentLayout().isSwipeInProgress()
                || getVisibleDialog() != null) return;
        PendingTransfer result = pendingTransfer;
        pendingTransfer = null;
        backupBusy = false;
        if (result.generation != transferGeneration) return;
        if (result.status == MontogramBackupIO.Status.CANCELLED) return;
        if (result.status == MontogramBackupIO.Status.TIMED_OUT
                || result.status == MontogramBackupIO.Status.UNAVAILABLE_AFTER_TIMEOUT) {
            showBackupMessage(R.string.MontogramBackupUnavailable);
        } else if (result.status == MontogramBackupIO.Status.BUSY) {
            showBackupMessage(R.string.MontogramBackupBusy);
        } else if (result.status != MontogramBackupIO.Status.SUCCESS) {
            showBackupMessage(R.string.MontogramBackupError);
        } else if (result.exporting) {
            showBackupMessage(R.string.MontogramBackupExported);
        } else if (result.revision != MontogramConfig.revision()) {
            showBackupMessage(R.string.MontogramSettingsStale);
        } else if (result.value != null) {
            State current=MontogramConfig.snapshot();
            confirmChange(new MontogramSettingsChange(current,MontogramConfig.revision(),result.value.merge(current)),
                ++changeGeneration, R.string.MontogramBackupConfirm);
        }
    }

    private void showBackupMessage(int message) {
        if (backupDestroyed || getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle(text(R.string.MontogramBackupTitle))
            .setMessage(text(message)).setPositiveButton(text(R.string.OK), null).create());
    }

    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }

    public boolean previewMaterialPalette(boolean dark) {
        if (getParentActivity() == null) return false;
        final String asset = dark ? "montogram-material-dark.attheme" : "montogram-material-light.attheme";
        // Fixed owned filename; never writes over imported/user theme files.
        File file = new File(getParentActivity().getCacheDir(), asset);
        try (InputStream input = getParentActivity().getAssets().open(asset);
             FileOutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        } catch (Exception error) { showPaletteError(); return false; }
        String title = dark ? "Montogram Material Dark" : "Montogram Material Light";
        Theme.ThemeInfo info = Theme.applyThemeFile(file, title, null, true);
        if (info == null) { showPaletteError(); return false; }
        boolean presented = presentFragment(new ThemePreviewActivity(info, true, ThemePreviewActivity.SCREEN_TYPE_PREVIEW, false, false));
        if (!presented) Theme.applyPreviousTheme();
        return presented;
    }

    private void showPaletteError() {
        if (getParentActivity() == null) return;
        new AlertDialog.Builder(getParentActivity(), getResourceProvider()).setTitle("Montogram")
            .setMessage(text(R.string.MontogramPaletteError))
            .setPositiveButton(text(R.string.OK), null).show();
    }
}
