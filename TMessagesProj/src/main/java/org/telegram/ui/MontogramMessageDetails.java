/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;

import android.content.Context;
import android.view.View;
import java.util.ArrayList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.MontogramMaterial;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

/** Read-only scalar snapshot: no message body, local file paths, account lookups or requests. */
public final class MontogramMessageDetails extends UniversalFragment {
    public static final class Snapshot {
        public final int account, messageId, date, editedDate;
        public final long dialogId, senderId, fileBytes;
        public final String fileName, mimeType;
        public Snapshot(int account, int messageId, long dialogId, long senderId, int date,
                int editedDate, String fileName, String mimeType, long fileBytes) {
            this.account = account;
            this.messageId = messageId;
            this.dialogId = dialogId;
            this.senderId = senderId;
            this.date = date;
            this.editedDate = editedDate;
            this.fileName = fileName == null ? "" : fileName;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.fileBytes = fileBytes;
        }
    }
    private final Snapshot snapshot;

    public MontogramMessageDetails(Snapshot snapshot) {
        this.snapshot = snapshot;
        setCurrentAccount(snapshot.account);
    }

    @Override public Theme.ResourcesProvider getResourceProvider() {
        return MontogramMaterial.scoped(super.getResourceProvider());
    }
    @Override public View createView(Context context) {
        View view = super.createView(context);
        actionBar.applyMontogramMaterial3();
        view.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        return view;
    }
    @Override protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MontogramMessageDetails);
    }
    private String valueForRow(int id) {
        switch (id) {
            case 1: return Integer.toString(snapshot.messageId);
            case 2: return Long.toString(snapshot.dialogId);
            case 3: return snapshot.senderId == 0 ? "" : Long.toString(snapshot.senderId);
            case 4: return snapshot.date > 0 ? LocaleController.formatDateTime(snapshot.date, false) : "";
            case 5: return snapshot.editedDate > 0 ? LocaleController.formatDateTime(snapshot.editedDate, false) : "";
            case 6: return snapshot.fileName;
            case 7: return snapshot.mimeType;
            case 8: return snapshot.fileBytes >= 0 ? Long.toString(snapshot.fileBytes) : "";
            default: return "";
        }
    }
    private void row(ArrayList<UItem> items, int id, int title) {
        String value = valueForRow(id);
        if (!value.isEmpty()) items.add(UItem.asButton(id, LocaleController.getString(title), value));
    }
    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        row(items, 1, R.string.MontogramMessageId);
        row(items, 2, R.string.MontogramDialogId);
        row(items, 3, R.string.MontogramSenderId);
        row(items, 4, R.string.MontogramSentAt);
        row(items, 5, R.string.MontogramEditedAt);
        row(items, 6, R.string.MontogramFileName);
        row(items, 7, R.string.MontogramMimeType);
        row(items, 8, R.string.MontogramFileBytes);
        items.add(UItem.asShadow(LocaleController.getString(R.string.MontogramMessageDetailsInfo)));
    }
    private boolean copy(int id) {
        String value = valueForRow(id);
        if (value.isEmpty()) return false;
        AndroidUtilities.addToClipboard(value);
        BulletinFactory.of(this).createCopyBulletin(LocaleController.getString(R.string.TextCopied), getResourceProvider()).show();
        return true;
    }
    @Override protected void onClick(UItem item, View view, int position, float x, float y) { copy(item.id); }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return copy(item.id); }
}
