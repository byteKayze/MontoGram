/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.net.Uri;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/** Typed settings codec adapter; provider work is shared with all local-data transfers. */
public final class MontogramBackupIO {
    public enum Status { SUCCESS, FAILED, BUSY, CANCELLED, TIMED_OUT, UNAVAILABLE_AFTER_TIMEOUT }
    public interface Handle { void cancel(); }
    public interface Callback { void complete(Status status, MontogramSettingsBackup.Snapshot snapshot); }
    private MontogramBackupIO() {}
    public static boolean isAvailable(){return MontogramSafIO.isAvailable();}
    public static boolean isBusy(){return MontogramSafIO.isBusy();}
    public static Handle transfer(Uri uri,boolean exporting,MontogramSettingsBackup.Snapshot snapshot,Callback callback){
        if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("UI thread required");
        if(callback==null)throw new IllegalArgumentException("Missing callback");
        byte[] outgoing=null;
        try{
            if(exporting){if(snapshot==null)throw new IllegalArgumentException("Missing snapshot");outgoing=snapshot.encode();}
        }catch(Exception invalid){AndroidUtilities.runOnUIThread(()->callback.complete(Status.FAILED,null));return ()->{};}
        MontogramSafIO.Handle handle=MontogramSafIO.transfer(uri,outgoing,4096,(status,bytes)->{
            Status outcome=Status.valueOf(status.name());MontogramSettingsBackup.Snapshot value=null;
            try{if(outcome==Status.SUCCESS&&!exporting)value=MontogramSettingsBackup.read(new ByteArrayInputStream(bytes));}
            catch(Exception invalid){outcome=Status.FAILED;}
            finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);}
            callback.complete(outcome,value);
        });
        if(outgoing!=null)Arrays.fill(outgoing,(byte)0);
        return handle::cancel;
    }
}
