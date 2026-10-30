/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.os.Looper;
import org.telegram.messenger.MontogramLocalBlob.Stale;
import org.telegram.messenger.MontogramLocalBlob.Corrupt;
import org.telegram.messenger.MontogramLocalBlob.Unknown;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Templates only. Default writes stay in this process. Retention is a separate,
 * explicit user action; file existence records a previously accepted choice.
 * No auto-save/promotion, Telegram data, auth preferences or send operations.
 */
public final class MontogramQuickReplies {
    public enum Mode { PROCESS_MEMORY, RETAINED }
    public enum Status { OK, STALE, BUSY, INVALID, CORRUPT, KEY_ERROR, IO_ERROR, COMMIT_UNKNOWN }
    public interface Callback { void complete(Status status, Snapshot snapshot); }
    public static final class Snapshot {
        private final MontogramQuickReplyMemory.Snapshot owner;
        private final Mode mode;
        private Snapshot(MontogramQuickReplyMemory.Snapshot owner, Mode mode) { this.owner = owner; this.mode = mode; }
    }
    public static final class Request {
        private final MontogramQuickReplyMemory.Snapshot owner;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private boolean completed;
        private Request(MontogramQuickReplyMemory.Snapshot owner) { this.owner = owner; }
        /** Cancels disclosure/ordinary pending work. Confirmed deletion may finish. */
        public void cancel() {
            ui();
            if (completed) return;
            cancelled.set(true);
            MontogramQuickReplyAccess.revoke(owner);
        }
    }
    private static final boolean[] BUSY = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private MontogramQuickReplies() {}
    private static void ui() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
    }
    public static boolean current(Snapshot value) { ui(); return value != null && MontogramQuickReplyAccess.isCurrent(value.owner); }
    public static Mode mode(Snapshot value) { return current(value) ? value.mode : null; }
    public static List<MontogramQuickReplyData.Reply> entries(Snapshot value) { return current(value) ? MontogramQuickReplyAccess.replies(value.owner) : null; }
    public static byte[] exportPortable(Snapshot value){
        ui();if(!current(value))throw new IllegalStateException("Stale local owner");
        return MontogramLocalExport.replies(value.owner.data.replies);
    }
    public static int serializedBytes(Snapshot value) throws java.io.IOException {
        ui();if(!current(value))throw new IllegalStateException("Stale local owner");
        byte[] bytes=value.owner.data.encode();try{return bytes.length;}finally{java.util.Arrays.fill(bytes,(byte)0);}
    }
    public static void revoke(Snapshot value) { ui(); if (value != null) MontogramQuickReplyAccess.revoke(value.owner); }

    private static final class Result {
        final MontogramQuickReplyData data;
        final Mode mode;
        final boolean install;
        Result(MontogramQuickReplyData data, Mode mode, boolean install) { this.data = data; this.mode = mode; this.install = install; }
    }
    private interface Task { Result run() throws Exception; }
    private static Request queue(MontogramQuickReplyMemory.Snapshot owner, Callback callback, boolean confirmedDeletion, Task task) {
        ui();
        if (!MontogramQuickReplyAccess.isCurrent(owner)) { callback.complete(Status.STALE, null); return null; }
        if (BUSY[owner.account]) { callback.complete(Status.BUSY, null); return null; }
        BUSY[owner.account] = true;
        Request request = new Request(owner);
        try {
            Utilities.globalQueue.postRunnable(() -> {
                Status status = Status.OK; Result result = null;
                try {
                    if (!confirmedDeletion && (request.cancelled.get() || !MontogramQuickReplyMemory.global().isCurrent(owner))) throw new Stale();
                    result = task.run();
                } catch (Stale failure) { status = Status.STALE; }
                catch (Corrupt failure) { status = Status.CORRUPT; }
                catch (Unknown failure) { status = Status.COMMIT_UNKNOWN; }
                catch (GeneralSecurityException failure) { status = Status.KEY_ERROR; }
                catch (IllegalArgumentException failure) { status = Status.INVALID; }
                catch (Exception failure) { status = Status.IO_ERROR; }
                final Status outcome = status; final Result value = result;
                AndroidUtilities.runOnUIThread(() -> {
                    BUSY[owner.account] = false; request.completed = true;
                    if (request.cancelled.get() || !MontogramQuickReplyAccess.isCurrent(owner)) { callback.complete(Status.STALE, null); return; }
                    if (outcome != Status.OK) {
                        if (outcome == Status.KEY_ERROR || outcome == Status.CORRUPT || outcome == Status.IO_ERROR || outcome == Status.COMMIT_UNKNOWN) {
                            MontogramQuickReplyAccess.revoke(owner);
                        }
                        callback.complete(outcome, null); return;
                    }
                    MontogramQuickReplyMemory.Snapshot ready = value.install ? MontogramQuickReplyMemory.global().replace(owner, value.data) : owner;
                    if (!MontogramQuickReplyAccess.isCurrent(ready)) { callback.complete(Status.STALE, null); return; }
                    callback.complete(Status.OK, new Snapshot(ready, value.mode));
                });
            });
        } catch (RuntimeException unavailable) {
            BUSY[owner.account] = false; request.completed = true;
            callback.complete(Status.IO_ERROR, null);
        }
        return request;
    }

    public static Request load(int account, Callback callback) {
        ui();
        MontogramQuickReplyMemory.Snapshot owner = MontogramQuickReplyAccess.acquire(account);
        if (owner == null) { callback.complete(Status.STALE, null); return null; }
        return queue(owner, callback, false, () -> {
            MontogramQuickReplyData retained = read(owner);
            return retained == null ? new Result(owner.data, Mode.PROCESS_MEMORY, false) : new Result(retained, Mode.RETAINED, true);
        });
    }

    public static Request save(Snapshot previous, long id, String label, String text, Callback callback) {
        return change(previous, id, label, text, false, callback);
    }
    public static Request delete(Snapshot previous, long id, Callback callback) { return change(previous, id, null, null, true, callback); }
    private static Request change(Snapshot previous, long id, String label, String text, boolean remove, Callback callback) {
        ui();
        if (!current(previous)) { callback.complete(Status.STALE, null); return null; }
        if (BUSY[previous.owner.account]) { callback.complete(Status.BUSY, null); return null; }
        final MontogramQuickReplyData target;
        try { target = remove ? previous.owner.data.delete(id) : id == 0 ? previous.owner.data.add(label, text) : previous.owner.data.edit(id, label, text); }
        catch (IllegalArgumentException invalid) { callback.complete(Status.INVALID, null); return null; }
        return commit(previous,target,callback);
    }
    public static long revision(Snapshot value){ui();return current(value)?value.owner.data.revision:-1;}
    public static int previewMergeCount(Snapshot value,MontogramQuickReplyData incoming){ui();if(!current(value))throw new IllegalStateException("Stale local owner");return value.owner.data.mergeImported(incoming).replies.size();}
    public static Request mergeImported(Snapshot previous,MontogramQuickReplyData incoming,String expectedDigest,long expectedRevision,Callback callback){
        ui();if(!current(previous)){callback.complete(Status.STALE,null);return null;}
        if(previous.owner.data.revision!=expectedRevision){callback.complete(Status.STALE,null);return null;}
        if(BUSY[previous.owner.account]){callback.complete(Status.BUSY,null);return null;}
        final MontogramQuickReplyData target;
        try{
            byte[] canonical=incoming.encode();
            try{if(!java.util.Arrays.equals(java.security.MessageDigest.getInstance("SHA-256").digest(canonical),hexDigest(expectedDigest)))throw new IllegalArgumentException("Import changed");}
            finally{java.util.Arrays.fill(canonical,(byte)0);}
            target=previous.owner.data.mergeImported(incoming);
        }catch(Exception invalid){callback.complete(Status.INVALID,null);return null;}
        return commit(previous,target,callback);
    }
    private static byte[] hexDigest(String value){
        if(value==null||!value.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid import digest");
        byte[] bytes=new byte[32];for(int i=0;i<32;i++)bytes[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return bytes;
    }
    private static Request commit(Snapshot previous,MontogramQuickReplyData target,Callback callback){
        if (target == previous.owner.data) { callback.complete(Status.OK, previous); return null; }
        if (previous.mode == Mode.PROCESS_MEMORY) {
            MontogramQuickReplyMemory.Snapshot ready = MontogramQuickReplyMemory.global().replace(previous.owner, target);
            callback.complete(MontogramQuickReplyAccess.isCurrent(ready) ? Status.OK : Status.STALE,
                MontogramQuickReplyAccess.isCurrent(ready) ? new Snapshot(ready, Mode.PROCESS_MEMORY) : null);
            return null;
        }
        return queue(previous.owner, callback, false, () -> {
            MontogramQuickReplyData live = read(previous.owner);
            if (live == null || !Arrays.equals(live.encode(), previous.owner.data.encode())) throw new Stale();
            write(previous.owner, target, false);
            return new Result(target, Mode.RETAINED, true);
        });
    }

    /** Call only from the affirmative retention dialog, never load/save/reset/import. */
    public static Request retainAfterConfirmedUserAction(Snapshot previous, Callback callback) {
        ui();
        if (!current(previous)) { callback.complete(Status.STALE, null); return null; }
        if (previous.mode == Mode.RETAINED) { callback.complete(Status.OK, previous); return null; }
        return queue(previous.owner, callback, false, () -> {
            if (MontogramLocalBlob.exists(MontogramLocalFeature.QUICK_REPLIES,previous.owner.account,previous.owner.self)) throw new Stale();
            write(previous.owner, previous.owner.data, true);
            return new Result(previous.owner.data, Mode.RETAINED, true);
        });
    }

    /** Explicit reset also works after a corrupt file or missing/invalidated key.
     * Once accepted, deletion can finish after pause/logout; it only targets the
     * captured owner's namespace. No new operation enters that slot meanwhile.
     */
    public static Request resetRetained(int account, Callback callback) {
        ui();
        MontogramQuickReplyMemory.Snapshot owner = MontogramQuickReplyAccess.acquire(account);
        if (owner == null) { callback.complete(Status.STALE, null); return null; }
        if (BUSY[account]) { callback.complete(Status.BUSY, null); return null; }
        owner = MontogramQuickReplyAccess.clear(owner);
        if (owner == null) { callback.complete(Status.STALE, null); return null; }
        final MontogramQuickReplyMemory.Snapshot cleared = owner;
        return queue(cleared, callback, true, () -> {
            MontogramLocalBlob.delete(MontogramLocalFeature.QUICK_REPLIES,cleared.account,cleared.self);
            return new Result(cleared.data, Mode.PROCESS_MEMORY, false);
        });
    }
    public static Request clear(Snapshot previous, Callback callback) {
        ui();
        if (!current(previous)) { callback.complete(Status.STALE, null); return null; }
        if (BUSY[previous.owner.account]) { callback.complete(Status.BUSY, null); return null; }
        if (previous.mode == Mode.RETAINED) return resetRetained(previous.owner.account, callback);
        MontogramQuickReplyMemory.Snapshot cleared = MontogramQuickReplyAccess.clear(previous.owner);
        callback.complete(MontogramQuickReplyAccess.isCurrent(cleared) ? Status.OK : Status.STALE,
            MontogramQuickReplyAccess.isCurrent(cleared) ? new Snapshot(cleared, Mode.PROCESS_MEMORY) : null);
        return null;
    }

    private static MontogramQuickReplyData read(MontogramQuickReplyMemory.Snapshot owner) throws Exception {
        byte[] clear=MontogramLocalBlob.read(MontogramLocalFeature.QUICK_REPLIES,owner.account,owner.self);
        if(clear==null)return null;
        try{return MontogramQuickReplyData.decode(clear);}
        catch(IOException malformed){throw new Corrupt();}
        finally{Arrays.fill(clear,(byte)0);}
    }
    private static void write(MontogramQuickReplyMemory.Snapshot owner,MontogramQuickReplyData data,boolean explicitRetention)throws Exception{
        byte[] clear=data.encode();
        try{MontogramLocalBlob.write(MontogramLocalFeature.QUICK_REPLIES,owner.account,owner.self,clear,()->MontogramQuickReplyMemory.global().isCurrent(owner),explicitRetention);}
        finally{Arrays.fill(clear,(byte)0);}
    }
}
