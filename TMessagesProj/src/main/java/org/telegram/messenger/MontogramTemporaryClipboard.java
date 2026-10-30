/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import java.lang.ref.WeakReference;
import java.util.UUID;
/** One best-effort temporary plain-text clip. No listeners, services or worker threads. */
public final class MontogramTemporaryClipboard {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Pending pending;
    private MontogramTemporaryClipboard(){}
    private static void ui(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("UI thread required");}
    private static final class Pending implements Runnable {
        Object scope;String label,payload;final WeakReference<Activity> activity;final int account;final long self;
        Pending(Object scope,Activity activity,int account,long self,String payload){this.scope=scope;this.activity=new WeakReference<>(activity);this.account=account;this.self=self;this.payload=payload;label="Montogram/"+UUID.randomUUID();}
        boolean foreground(){Activity a=activity.get();return a!=null&&!a.isFinishing()&&!a.isDestroyed()&&a.hasWindowFocus()&&account==UserConfig.selectedAccount&&UserConfig.getInstance(account).isClientActivated()&&self>0&&self==UserConfig.getInstance(account).getClientUserId()&&!SharedConfig.appLocked&&!SharedConfig.isWaitingForPasscodeEnter;}
        boolean owns(ClipDescription description){return description!=null&&description.getLabel()!=null&&label.contentEquals(description.getLabel())&&description.getMimeTypeCount()==1&&ClipDescription.MIMETYPE_TEXT_PLAIN.equals(description.getMimeType(0));}
        ClipboardManager manager(){Context context=ApplicationLoader.applicationContext;return context==null?null:(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);}
        void forget(){MAIN.removeCallbacks(this);scope=null;label=null;payload=null;activity.clear();if(pending==this)pending=null;}
        @Override public void run(){
            ui();if(pending!=this){forget();return;}
            try{
                // Clearing is authorized by the opaque clip token, not by the current account/window.
                ClipboardManager clipboard=manager();if(clipboard==null)return;
                ClipDescription description=clipboard.getPrimaryClipDescription();
                if(!owns(description))return;
                ClipData clip=clipboard.getPrimaryClip();
                if(clip==null||clip.getItemCount()!=1||!owns(clip.getDescription()))return;
                ClipData.Item item=clip.getItemAt(0);CharSequence text=item.getText();
                if(item.getUri()!=null||item.getIntent()!=null||item.getHtmlText()!=null||text==null||!payload.contentEquals(text))return;
                if(Build.VERSION.SDK_INT>=28)clipboard.clearPrimaryClip();else clipboard.setPrimaryClip(ClipData.newPlainText("",""));
            }catch(Exception inaccessible){/* Clipboard access may be denied; never log its contents. */}
            finally{forget();}
        }
    }
    public static boolean copy(Object scope,Activity activity,int account,long self,String text){
        ui();if(scope==null||activity==null||account<0||account>=UserConfig.MAX_ACCOUNT_COUNT)return false;
        try{MontogramReaderSearch.validate(text,MontogramReaderSearch.MAX_TEXT);}catch(IllegalArgumentException invalid){return false;}
        Pending next=new Pending(scope,activity,account,self,text);
        if(!next.foreground()){next.forget();return false;}
        try{
            ClipboardManager clipboard=next.manager();if(clipboard==null){next.forget();return false;}
            ClipData clip=ClipData.newPlainText(next.label,text);
            if(Build.VERSION.SDK_INT>=24){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}
            clipboard.setPrimaryClip(clip);if(pending!=null)pending.forget();pending=next;if(!MAIN.postDelayed(next,60000)){next.run();return false;}return true;
        }catch(Exception denied){next.forget();return false;}
    }
    public static void revoke(Object scope){ui();if(pending!=null&&pending.scope==scope)pending.run();}
}
