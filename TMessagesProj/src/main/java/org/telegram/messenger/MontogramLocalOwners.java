/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Fixed, bounded lifecycle fan-out. No files, Android auth, locks or queues. */
final class MontogramLocalOwners {
    static final MontogramOwnerMemory<MontogramQuickReplyData> QUICK_REPLIES=new MontogramOwnerMemory<>(UserConfig.MAX_ACCOUNT_COUNT,MontogramQuickReplyData::empty);
    static final MontogramOwnerMemory<MontogramBookmarkData> BOOKMARKS=new MontogramOwnerMemory<>(UserConfig.MAX_ACCOUNT_COUNT,MontogramBookmarkData::empty);
    static final MontogramOwnerMemory<MontogramNoteData> NOTES=new MontogramOwnerMemory<>(UserConfig.MAX_ACCOUNT_COUNT,MontogramNoteData::empty);
    static final class ClearTokens {
        private final MontogramOwnerMemory.ClearToken replies,bookmarks,notes;
        private ClearTokens(MontogramOwnerMemory.ClearToken replies,MontogramOwnerMemory.ClearToken bookmarks,MontogramOwnerMemory.ClearToken notes){this.replies=replies;this.bookmarks=bookmarks;this.notes=notes;}
    }
    static void revokeAll(){QUICK_REPLIES.revokeAll();BOOKMARKS.revokeAll();NOTES.revokeAll();}
    static ClearTokens beginClear(int account){
        if(account<0||account>=UserConfig.MAX_ACCOUNT_COUNT)return null;
        MontogramOwnerMemory.ClearToken replies=QUICK_REPLIES.beginClear(account),bookmarks=BOOKMARKS.beginClear(account),notes=NOTES.beginClear(account);
        return new ClearTokens(replies,bookmarks,notes);
    }
    static boolean finishClear(ClearTokens tokens,boolean completed){
        if(tokens==null)return false;
        boolean replies=QUICK_REPLIES.finishClear(tokens.replies,completed),bookmarks=BOOKMARKS.finishClear(tokens.bookmarks,completed),notes=NOTES.finishClear(tokens.notes,completed);
        return replies&&bookmarks&&notes;
    }
}
