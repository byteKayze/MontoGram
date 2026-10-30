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

/** User-authored whole-chat notes only. No Telegram content, networking or send operations. Default writes stay in this process. Retention is a separate,
 * explicit user action; file existence records a previously accepted choice.
 * No auto-save/promotion, Telegram data, auth preferences or send operations.
 */
public final class MontogramNotes {
    public enum Mode { PROCESS_MEMORY, RETAINED }
    public enum Status { OK, STALE, BUSY, INVALID, CORRUPT, KEY_ERROR, IO_ERROR, COMMIT_UNKNOWN }
    public interface Callback { void complete(Status status, Snapshot snapshot); }
    public static final class Snapshot {
        private final MontogramOwnerMemory.Snapshot<MontogramNoteData> owner;
        private final Mode mode;
        private Snapshot(MontogramOwnerMemory.Snapshot<MontogramNoteData> owner, Mode mode) { this.owner = owner; this.mode = mode; }
    }
    public static final class Request {
        private final MontogramOwnerMemory.Snapshot<MontogramNoteData> owner;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private boolean completed;
        private Request(MontogramOwnerMemory.Snapshot<MontogramNoteData> owner) { this.owner = owner; }
        /** Cancels disclosure/ordinary pending work. Confirmed deletion may finish. */
        public void cancel() {
            ui();
            if (completed) return;
            cancelled.set(true);
            MEMORY.revoke(owner);
        }
    }
    private static final MontogramOwnerMemory<MontogramNoteData> MEMORY=MontogramLocalOwners.NOTES;
    private static final boolean[] BUSY = new boolean[UserConfig.MAX_ACCOUNT_COUNT];
    private MontogramNotes() {}
    private static void ui() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
    }
    public static boolean current(Snapshot value) { ui(); return value != null && authorized(value.owner); }
    public static Mode mode(Snapshot value) { return current(value) ? value.mode : null; }
    public static List<MontogramNoteData.Entry> entries(Snapshot value) { return current(value) ? value.owner.data.entries : null; }
    public static byte[] exportPortable(Snapshot value){
        ui();if(!current(value))throw new IllegalStateException("Stale local owner");
        return MontogramLocalExport.notes(value.owner.data.entries);
    }
    public static int serializedBytes(Snapshot value) throws java.io.IOException {
        ui();if(!current(value))throw new IllegalStateException("Stale local owner");
        byte[] bytes=value.owner.data.encode();try{return bytes.length;}finally{java.util.Arrays.fill(bytes,(byte)0);}
    }
    public static void revoke(Snapshot value) { ui(); if (value != null) MEMORY.revoke(value.owner); }

    private static MontogramOwnerMemory.Snapshot<MontogramNoteData> acquire(int account){
        long self=MontogramQuickReplyAccess.authorizedSelf(account);if(self<=0)return null;
        MontogramOwnerMemory.Snapshot<MontogramNoteData> value=MEMORY.bind(account,self);
        return value!=null&&MontogramQuickReplyAccess.authorizedSelf(account)==self&&MEMORY.isCurrent(value)?value:null;
    }
    private static boolean authorized(MontogramOwnerMemory.Snapshot<MontogramNoteData> value){
        ui();if(value==null)return false;
        if(MontogramQuickReplyAccess.authorizedSelf(value.account)!=value.self){MEMORY.revoke(value);return false;}
        return MEMORY.isCurrent(value);
    }

    private static final class Result {
        final MontogramNoteData data;
        final Mode mode;
        final boolean install;
        Result(MontogramNoteData data, Mode mode, boolean install) { this.data = data; this.mode = mode; this.install = install; }
    }
    private interface Task { Result run() throws Exception; }
    private static Request queue(MontogramOwnerMemory.Snapshot<MontogramNoteData> owner, Callback callback, boolean confirmedDeletion, Task task) {
        ui();
        if (!authorized(owner)) { callback.complete(Status.STALE, null); return null; }
        if (BUSY[owner.account]) { callback.complete(Status.BUSY, null); return null; }
        BUSY[owner.account] = true;
        Request request = new Request(owner);
        try {
            Utilities.globalQueue.postRunnable(() -> {
                Status status = Status.OK; Result result = null;
                try {
                    if (!confirmedDeletion && (request.cancelled.get() || !MEMORY.isCurrent(owner))) throw new Stale();
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
                    if (request.cancelled.get() || !authorized(owner)) { callback.complete(Status.STALE, null); return; }
                    if (outcome != Status.OK) {
                        if (outcome == Status.KEY_ERROR || outcome == Status.CORRUPT || outcome == Status.IO_ERROR || outcome == Status.COMMIT_UNKNOWN) {
                            MEMORY.revoke(owner);
                        }
                        callback.complete(outcome, null); return;
                    }
                    MontogramOwnerMemory.Snapshot<MontogramNoteData> ready = value.install ? MEMORY.replace(owner, value.data) : owner;
                    if (!authorized(ready)) { callback.complete(Status.STALE, null); return; }
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
        MontogramOwnerMemory.Snapshot<MontogramNoteData> owner = acquire(account);
        if (owner == null) { callback.complete(Status.STALE, null); return null; }
        return queue(owner, callback, false, () -> {
            MontogramNoteData retained = read(owner);
            return retained == null ? new Result(owner.data, Mode.PROCESS_MEMORY, false) : new Result(retained, Mode.RETAINED, true);
        });
    }

    public static Request save(Snapshot previous,long dialogId,String text,Callback callback){return change(previous,value->value.put(dialogId,text),callback);}
    public static Request deleteSelected(Snapshot previous,long[] peers,Callback callback){long[] copy=peers==null?null:peers.clone();return change(previous,v->v.removeAll(copy),callback);}
    public static Request delete(Snapshot previous,long dialogId,Callback callback){return change(previous,value->value.remove(dialogId),callback);}
    private static Request change(Snapshot previous,java.util.function.UnaryOperator<MontogramNoteData> change,Callback callback){
        ui();
        if(!current(previous)){callback.complete(Status.STALE,null);return null;}
        if(BUSY[previous.owner.account]){callback.complete(Status.BUSY,null);return null;}
        final MontogramNoteData target;
        try{target=change.apply(previous.owner.data);}catch(IllegalArgumentException invalid){callback.complete(Status.INVALID,null);return null;}
        if(target==previous.owner.data){callback.complete(Status.OK,previous);return null;}
        if(previous.mode==Mode.PROCESS_MEMORY){
            MontogramOwnerMemory.Snapshot<MontogramNoteData> ready=MEMORY.replace(previous.owner,target);
            boolean allowed=authorized(ready);callback.complete(allowed?Status.OK:Status.STALE,allowed?new Snapshot(ready,Mode.PROCESS_MEMORY):null);return null;
        }
        return queue(previous.owner,callback,false,()->{
            MontogramNoteData live=read(previous.owner);
            if(live==null||!Arrays.equals(live.encode(),previous.owner.data.encode()))throw new Stale();
            write(previous.owner,target,false);return new Result(target,Mode.RETAINED,true);
        });
    }

    /** Call only from the affirmative retention dialog, never load/save/reset/import. */
    public static Request retainAfterConfirmedUserAction(Snapshot previous, Callback callback) {
        ui();
        if (!current(previous)) { callback.complete(Status.STALE, null); return null; }
        if (previous.mode == Mode.RETAINED) { callback.complete(Status.OK, previous); return null; }
        return queue(previous.owner, callback, false, () -> {
            if (MontogramLocalBlob.exists(MontogramLocalFeature.NOTES,previous.owner.account,previous.owner.self)) throw new Stale();
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
        MontogramOwnerMemory.Snapshot<MontogramNoteData> owner = acquire(account);
        if (owner == null) { callback.complete(Status.STALE, null); return null; }
        if (BUSY[account]) { callback.complete(Status.BUSY, null); return null; }
        owner = MEMORY.replace(owner,MontogramNoteData.empty());
        if (owner == null) { callback.complete(Status.STALE, null); return null; }
        final MontogramOwnerMemory.Snapshot<MontogramNoteData> cleared = owner;
        return queue(cleared, callback, true, () -> {
            MontogramLocalBlob.delete(MontogramLocalFeature.NOTES,cleared.account,cleared.self);
            return new Result(cleared.data, Mode.PROCESS_MEMORY, false);
        });
    }
    public static Request clear(Snapshot previous, Callback callback) {
        ui();
        if (!current(previous)) { callback.complete(Status.STALE, null); return null; }
        if (BUSY[previous.owner.account]) { callback.complete(Status.BUSY, null); return null; }
        if (previous.mode == Mode.RETAINED) return resetRetained(previous.owner.account, callback);
        MontogramOwnerMemory.Snapshot<MontogramNoteData> cleared = MEMORY.replace(previous.owner,MontogramNoteData.empty());
        callback.complete(authorized(cleared) ? Status.OK : Status.STALE,
            authorized(cleared) ? new Snapshot(cleared, Mode.PROCESS_MEMORY) : null);
        return null;
    }

    private static MontogramNoteData read(MontogramOwnerMemory.Snapshot<MontogramNoteData> owner) throws Exception {
        byte[] clear=MontogramLocalBlob.read(MontogramLocalFeature.NOTES,owner.account,owner.self);
        if(clear==null)return null;
        try{return MontogramNoteData.decode(clear);}
        catch(IOException malformed){throw new Corrupt();}
        finally{Arrays.fill(clear,(byte)0);}
    }
    private static void write(MontogramOwnerMemory.Snapshot<MontogramNoteData> owner,MontogramNoteData data,boolean explicitRetention)throws Exception{
        byte[] clear=data.encode();
        try{MontogramLocalBlob.write(MontogramLocalFeature.NOTES,owner.account,owner.self,clear,()->MEMORY.isCurrent(owner),explicitRetention);}
        finally{Arrays.fill(clear,(byte)0);}
    }
}
