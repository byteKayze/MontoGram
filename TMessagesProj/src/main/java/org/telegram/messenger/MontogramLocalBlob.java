/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.os.Process;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.AtomicFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import javax.crypto.SecretKey;

/** Worker-only bounded encrypted blobs, with no auth, codec, consent or UI policy.
 * A caller must supply a captured owner and a pure memory-capability check.
 */
final class MontogramLocalBlob {
    static final class Stale extends IOException {}
    static final class Corrupt extends IOException {}
    static final class Unknown extends IOException {}
    private MontogramLocalBlob(){}
    private static File[] files(File base){return new File[]{base,new File(base+".bak"),new File(base+".new")};}
    private static boolean anyFile(File base){for(File path:files(base))if(path.exists())return true;return false;}
    private static void safe(File path,boolean directory)throws IOException{
        if(!path.getAbsolutePath().equals(path.getCanonicalPath()))throw new IOException("Aliased retained-data path");
        try{
            StructStat info=Os.lstat(path.getPath());
            if(OsConstants.S_ISLNK(info.st_mode)||info.st_uid!=Process.myUid()||
                (directory?!OsConstants.S_ISDIR(info.st_mode):!OsConstants.S_ISREG(info.st_mode))||
                (info.st_mode&0777)!=(directory?0700:0600))throw new IOException("Unsafe retained-data path");
        }catch(ErrnoException missing){if(missing.errno!=OsConstants.ENOENT)throw new IOException("Retained-data metadata unavailable");}
    }
    private static File file(MontogramLocalFeature feature,int account,long self,boolean create)throws IOException{
        if(feature==null||account<0||account>=UserConfig.MAX_ACCOUNT_COUNT||self<=0)throw new IOException("Invalid retained-data owner");
        if(ApplicationLoader.applicationContext==null)throw new IOException("Retained storage unavailable");
        File base=ApplicationLoader.applicationContext.getNoBackupFilesDir();
        if(base==null)throw new IOException("Retained storage unavailable");
        File folder=new File(base.getCanonicalFile(),feature.directory);
        if(create&&!folder.exists()){
            if(!folder.mkdir())throw new IOException("Retained directory unavailable");
            try{Os.chmod(folder.getPath(),0700);}catch(Exception failure){throw new IOException("Retained directory protection failed");}
        }
        safe(folder,true);File result=new File(folder,account+"-"+self+".bin");
        for(File path:files(result))safe(path,false);
        return result;
    }
    static boolean exists(MontogramLocalFeature feature,int account,long self)throws IOException{return anyFile(file(feature,account,self,false));}
    /** Caller owns returned plaintext bytes and must zero them after decoding. */
    static byte[] read(MontogramLocalFeature feature,int account,long self)throws Exception{
        File base=file(feature,account,self,false);if(!anyFile(base))return null;
        if(!base.exists()&&!new File(base+".bak").exists())throw new Corrupt();
        int maximum=MontogramLocalCipher.maxEnvelopeBytes(feature);ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(FileInputStream input=new AtomicFile(base).openRead()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer,0,Math.min(buffer.length,maximum+1-bytes.size())))!=-1){
                if(count<=0)throw new IOException("Invalid retained-data read");bytes.write(buffer,0,count);
                if(bytes.size()>maximum)throw new Corrupt();
            }
        }
        SecretKey key=MontogramLocalKeys.get(feature,account,self,false);
        return MontogramLocalCipher.open(feature,key,account,self,bytes.toByteArray());
    }
    /** Explicit-retention creation is passed only by an affirmative typed UI action. */
    static void write(MontogramLocalFeature feature,int account,long self,byte[] clear,BooleanSupplier current,boolean explicitRetention)throws Exception{
        if(!current.getAsBoolean())throw new Stale();
        SecretKey key=MontogramLocalKeys.get(feature,account,self,explicitRetention);
        byte[] encrypted=MontogramLocalCipher.seal(feature,key,account,self,clear);
        AtomicFile file=new AtomicFile(file(feature,account,self,true));FileOutputStream output=null;boolean committing=false;
        try{
            output=file.startWrite();Os.fchmod(output.getFD(),0600);output.write(encrypted);output.getFD().sync();
            if(!current.getAsBoolean())throw new Stale();
            committing=true;file.finishWrite(output);output=null;
            byte[] verified=read(feature,account,self);
            try{if(verified==null||!Arrays.equals(verified,clear))throw new Unknown();}
            finally{if(verified!=null)Arrays.fill(verified,(byte)0);}
        }catch(Exception failure){
            if(output!=null){try{file.failWrite(output);}catch(RuntimeException rollbackFailed){throw new Unknown();}}
            if(committing)throw new Unknown();throw failure;
        }
    }
    /** Confirmed deletion may complete after UI cancellation, for this captured owner only. */
    static void delete(MontogramLocalFeature feature,int account,long self)throws Exception{
        File base=file(feature,account,self,false);
        for(File path:files(base))if(path.exists()&&!path.delete())throw new IOException("Retained-data deletion failed");
        MontogramLocalKeys.remove(feature,account,self);
        if(anyFile(base))throw new IOException("Retained-data deletion incomplete");
    }
}
