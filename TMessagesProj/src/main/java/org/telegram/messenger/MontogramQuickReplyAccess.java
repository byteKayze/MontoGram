/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.os.Looper;
import java.util.List;

/** Internal authenticated gateway to templates. UI uses MontogramQuickReplies. */
public final class MontogramQuickReplyAccess {
    private MontogramQuickReplyAccess() {}

    private static void uiThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
    }
    static long authorizedSelf(int account) {
        uiThread();
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || account != UserConfig.selectedAccount ||
                SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter) return 0;
        UserConfig config = UserConfig.getInstance(account);
        return config.isClientActivated() ? config.getClientUserId() : 0;
    }
    static MontogramQuickReplyMemory.Snapshot acquire(int account) {
        uiThread();
        long owner = authorizedSelf(account);
        if (owner <= 0) return null;
        MontogramQuickReplyMemory.Snapshot snapshot = MontogramQuickReplyMemory.global().bind(account, owner);
        return snapshot != null && authorizedSelf(account) == owner && MontogramQuickReplyMemory.global().isCurrent(snapshot) ? snapshot : null;
    }
    static boolean isCurrent(MontogramQuickReplyMemory.Snapshot snapshot) {
        uiThread();
        if (snapshot == null) return false;
        if (authorizedSelf(snapshot.account) != snapshot.self) {
            MontogramQuickReplyMemory.global().revoke(snapshot);
            return false;
        }
        return MontogramQuickReplyMemory.global().isCurrent(snapshot);
    }
    /** Caller must discard rendered text on pause/lock; prior Java strings cannot
     * be revoked. This method rechecks authority before each new disclosure.
     */
    static List<MontogramQuickReplyData.Reply> replies(MontogramQuickReplyMemory.Snapshot snapshot) {
        return isCurrent(snapshot) ? snapshot.data.replies : null;
    }
    static MontogramQuickReplyMemory.Snapshot save(MontogramQuickReplyMemory.Snapshot previous, long id, String label, String text) {
        if (!isCurrent(previous)) return null;
        MontogramQuickReplyMemory.Snapshot saved = MontogramQuickReplyMemory.global().save(previous, id, label, text);
        return isCurrent(saved) ? saved : null;
    }
    static MontogramQuickReplyMemory.Snapshot remove(MontogramQuickReplyMemory.Snapshot previous, long id) {
        if (!isCurrent(previous)) return null;
        MontogramQuickReplyMemory.Snapshot saved = MontogramQuickReplyMemory.global().remove(previous, id);
        return isCurrent(saved) ? saved : null;
    }
    static MontogramQuickReplyMemory.Snapshot clear(MontogramQuickReplyMemory.Snapshot previous) {
        if (!isCurrent(previous)) return null;
        MontogramQuickReplyMemory.Snapshot cleared = MontogramQuickReplyMemory.global().replace(previous, MontogramQuickReplyData.empty());
        return isCurrent(cleared) ? cleared : null;
    }
    static void revoke(MontogramQuickReplyMemory.Snapshot snapshot) {
        uiThread();
        MontogramQuickReplyMemory.global().revoke(snapshot);
    }
    /** Native lock/account/pause boundary; any thread. Only revokes authority.
     * At most twelve small immutable Cell replacements across the three feature stores; no I/O, callbacks or locks.
     */
    public static void revokeAll() { MontogramQuickReplyMemory.global().revokeAll(); }
}
