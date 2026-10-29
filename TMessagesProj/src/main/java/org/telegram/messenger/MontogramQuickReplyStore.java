/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.os.Looper;
import android.system.Os;
import android.system.OsConstants;
import android.system.ErrnoException;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicLong;

/** Unwired backend: UI and native logout hook must be integrated together before use.
 * Plaintext, app-private no-backup data. Cleanup is best effort, not secure erasure.
 * All disk work, including logout cleanup, runs on the existing serial queue.
 */
public final class MontogramQuickReplyStore {
    private MontogramQuickReplyStore() {}
    public enum Status { OK, STALE, BUSY, INVALID, CORRUPT, IO_ERROR, COMMIT_UNKNOWN }
    public interface Callback { void complete(Status status, Snapshot snapshot); }
    // One atomic word: even generation permits access; odd generation is disabled.
    private static final AtomicLongArray STATES=new AtomicLongArray(UserConfig.MAX_ACCOUNT_COUNT);
    private static final AtomicInteger PENDING=new AtomicInteger();
    private static final AtomicLong ACCESS=new AtomicLong();
    private static final class Owner {
        final int account;final long self,epoch,access;
        Owner(int account,long self,long epoch){this.account=account;this.self=self;this.epoch=epoch;access=ACCESS.get();}
    }
    public static final class LogoutToken {
        private final int account;private final long self,epoch;
        private LogoutToken(int account,long self,long epoch){this.account=account;this.self=self;this.epoch=epoch;}
    }
    public static final class Snapshot {
        private final Owner owner;
        public final MontogramQuickReplyData data;
        private Snapshot(Owner owner,MontogramQuickReplyData data){this.owner=owner;this.data=data;}
    }
    private static void uiThread() {
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("UI thread required");
    }
    private static boolean enabled(Owner owner) {
        return (owner.epoch&1)==0 && STATES.get(owner.account)==owner.epoch
            && owner.access!=Long.MAX_VALUE && ACCESS.get()==owner.access;
    }
    private static boolean current(Owner owner) {
        return enabled(owner) && UserConfig.selectedAccount==owner.account
            && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter
            && UserConfig.getInstance(owner.account).isClientActivated()
            && UserConfig.getInstance(owner.account).getClientUserId()==owner.self;
    }
    public static void load(int account,Callback callback) {
        uiThread();
        if(account<0 || account>=UserConfig.MAX_ACCOUNT_COUNT){callback.complete(Status.STALE,null);return;}
        Owner owner=new Owner(account,UserConfig.getInstance(account).getClientUserId(),STATES.get(account));
        if(owner.self<=0 || !current(owner)){callback.complete(Status.STALE,null);return;}
        enqueue(owner,callback,()->read(owner));
    }
    public static void update(Snapshot previous,long id,String label,String text,Callback callback) {
        mutate(previous,id,label,text,false,callback);
    }
    public static void delete(Snapshot previous,long id,Callback callback) {
        mutate(previous,id,null,null,true,callback);
    }
    private static void mutate(Snapshot previous,long id,String label,String text,boolean remove,Callback callback) {
        uiThread();
        if(previous==null || !current(previous.owner)){callback.complete(Status.STALE,null);return;}
        // Bound payloads before retaining them on a shared queue.
        if(id<0 || !remove&&(label==null||text==null||label.length()>MontogramQuickReplyData.MAX_LABEL
                ||text.length()>MontogramQuickReplyData.MAX_TEXT)){callback.complete(Status.INVALID,null);return;}
        enqueue(previous.owner,callback,()->{
            MontogramQuickReplyData live=read(previous.owner);
            if(live.revision!=previous.data.revision)throw new Stale();
            MontogramQuickReplyData target=remove?live.delete(id):id==0?live.add(label,text):live.edit(id,label,text);
            if(target==live)return live;
            write(previous.owner,target);
            return target;
        });
    }
    private interface Work { MontogramQuickReplyData run() throws Exception; }
    private static final class Stale extends IOException {}
    private static final class Corrupt extends IOException {}
    private static final class UnknownCommit extends IOException {}
    private static void deliver(Owner owner,Status status,MontogramQuickReplyData data,Callback callback) {
        AndroidUtilities.runOnUIThread(()->{
            if(!current(owner)){callback.complete(Status.STALE,null);return;}
            callback.complete(status,status==Status.OK?new Snapshot(owner,data):null);
        });
    }
    private static void enqueue(Owner owner,Callback callback,Work work) {
        if(PENDING.incrementAndGet()>4){PENDING.decrementAndGet();callback.complete(Status.BUSY,null);return;}
        try {
            Utilities.globalQueue.postRunnable(()->{
                Status status=Status.OK;MontogramQuickReplyData data=null;
                try {
                    if(!enabled(owner))throw new Stale();
                    data=work.run();
                } catch(Stale stale){status=Status.STALE;}
                catch(Corrupt corrupt){status=Status.CORRUPT;}
                catch(UnknownCommit unknown){status=Status.COMMIT_UNKNOWN;}
                catch(IllegalArgumentException invalid){status=Status.INVALID;}
                catch(Exception unavailable){status=Status.IO_ERROR;}
                finally{PENDING.decrementAndGet();}
                deliver(owner,status,data,callback);
            });
        } catch(RuntimeException unavailable){PENDING.decrementAndGet();callback.complete(Status.IO_ERROR,null);}
    }
    private static void safe(File path) throws IOException {
        if(!path.getAbsolutePath().equals(path.getCanonicalPath()))throw new IOException("Aliased local path");
        try {
            if(OsConstants.S_ISLNK(Os.lstat(path.getPath()).st_mode))throw new IOException("Aliased local path");
        } catch(ErrnoException unavailable) {
            if(unavailable.errno!=OsConstants.ENOENT)throw new IOException("Unavailable local path");
        }
    }
    private static File root(boolean create) throws IOException {
        if(ApplicationLoader.applicationContext==null)throw new IOException("Storage unavailable");
        File base=ApplicationLoader.applicationContext.getNoBackupFilesDir();
        if(base==null)throw new IOException("Storage unavailable");
        File root=new File(base.getCanonicalFile(),"montogram-quick-replies");
        directory(root,create);return root;
    }
    private static void directory(File directory,boolean create) throws IOException {
        safe(directory);
        if(!directory.exists() && create) {
            if(!directory.mkdir())throw new IOException("Cannot create local directory");
        }
        if(directory.exists()&&!directory.isDirectory())throw new IOException("Invalid local directory");
        if(directory.exists()) {
            try{Os.chmod(directory.getPath(),0700);}catch(Exception unavailable){throw new IOException("Cannot protect local directory");}
        }
    }
    private static File folder(Owner owner,boolean create) throws IOException {
        File folder=new File(root(create),owner.account+"-"+owner.self);directory(folder,create);
        File marker=new File(folder,".deleted");safe(marker);
        if(marker.exists())throw new Stale();
        for(String name:new String[]{"replies.bin","replies.bin.bak","replies.bin.new"}) {
            File file=new File(folder,name);safe(file);
            if(file.exists()&&!file.isFile())throw new IOException("Invalid local file");
        }
        return folder;
    }
    private static MontogramQuickReplyData read(Owner owner) throws IOException {
        File folder=folder(owner,false);File base=new File(folder,"replies.bin");
        if(!base.exists()&&!new File(folder,"replies.bin.bak").exists())return MontogramQuickReplyData.empty();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(FileInputStream input=new AtomicFile(base).openRead()) {
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer,0,Math.min(buffer.length,MontogramQuickReplyData.MAX_BYTES+1-bytes.size())))!=-1) {
                if(count==0)throw new IOException("Invalid local read");
                bytes.write(buffer,0,count);
                if(bytes.size()>MontogramQuickReplyData.MAX_BYTES)throw new Corrupt();
            }
        }
        try{return MontogramQuickReplyData.decode(bytes.toByteArray());}
        catch(IOException invalid){throw new Corrupt();}
    }
    private static void write(Owner owner,MontogramQuickReplyData data) throws IOException {
        if(!enabled(owner))throw new Stale();
        File folder=folder(owner,true);AtomicFile file=new AtomicFile(new File(folder,"replies.bin"));
        byte[] payload=data.encode();FileOutputStream output=null;boolean committing=false;
        try {
            output=file.startWrite();
            try{Os.fchmod(output.getFD(),0600);}catch(Exception unavailable){throw new IOException("Cannot protect local file");}
            output.write(payload);output.getFD().sync();
            if(!enabled(owner))throw new Stale();
            committing=true;file.finishWrite(output);output=null;
            if(!Arrays.equals(read(owner).encode(),payload))throw new UnknownCommit();
        } catch(IOException|RuntimeException failure) {
            if(output!=null) {
                try{file.failWrite(output);}catch(RuntimeException rollbackFailed){throw new UnknownCommit();}
            }
            if(committing)throw new UnknownCommit();
            throw failure;
        }
    }
    private static void enableAfterCleanup(int account,long epoch) {
        if(epoch!=Long.MAX_VALUE)STATES.compareAndSet(account,epoch,epoch-1);
    }
    /** UI integration must call this on lock, account changes and owning-screen pause. */
    public static void revokeAccess() {
        long before;
        do{before=ACCESS.get();}while(before!=Long.MAX_VALUE&&!ACCESS.compareAndSet(before,before+1));
    }
    /** Capture at logout intent, not in a delayed completion or after identity clearing. */
    public static LogoutToken captureLogout(int account,long self) {
        uiThread();
        if(account<0||account>=UserConfig.MAX_ACCOUNT_COUNT||self<=0
                ||UserConfig.getInstance(account).getClientUserId()!=self)return null;
        long epoch=STATES.get(account);
        return (epoch&1)==0?new LogoutToken(account,self,epoch):null;
    }
    /** Must be called before native identity clearing; never waits for IO or a lock. */
    public static void onLogout(LogoutToken token) {
        if(token==null)return;
        final int account=token.account;final long self=token.self;
        final long epoch=token.epoch>=Long.MAX_VALUE-2?Long.MAX_VALUE:token.epoch+3;
        if(!STATES.compareAndSet(account,token.epoch,epoch))return;
        try {
            Utilities.globalQueue.postRunnable(()->{
                boolean cleaned=false;
                try {
                    File root=root(false),folder=new File(root,account+"-"+self);safe(folder);
                    if(!folder.exists()){cleaned=true;}
                    else {
                        File quarantine=new File(root,"deleted-"+account+"-"+self+"-"+UUID.randomUUID());
                        if(folder.renameTo(quarantine)) { cleaned=true;purgeKnown(quarantine); }
                        else if(purgeKnown(folder))cleaned=true;
                        else {
                            File marker=new File(folder,".deleted");safe(marker);
                            FileDescriptor descriptor=Os.open(marker.getPath(),OsConstants.O_WRONLY
                                |OsConstants.O_CREAT|OsConstants.O_TRUNC|OsConstants.O_NOFOLLOW,0600);
                            try{Os.fchmod(descriptor,0600);Os.fsync(descriptor);}finally{Os.close(descriptor);}
                            safe(marker);
                            if(!marker.isFile())throw new IOException("Invalid cleanup marker");
                        }
                    }
                } catch(Exception unavailable){/* No bodies, identities or private paths in logs. */}
                if(cleaned)enableAfterCleanup(account,epoch);
            });
        } catch(RuntimeException unavailable){/* Slot remains disabled for this process. */}
    }
    private static boolean purgeKnown(File folder) {
        for(String name:new String[]{"replies.bin","replies.bin.bak","replies.bin.new",".deleted"}) {
            File file=new File(folder,name);
            // File.delete unlinks a symlink itself; never traverse it or unknown subdirectories.
            if(file.exists()&&!file.delete())return false;
        }
        return folder.delete()||!folder.exists();
    }
}
