/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui.Components.voip;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MontogramCallRequest;
import org.telegram.messenger.MontogramConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;

/** Only explicit personal outgoing calls; incoming/group service starts never enter here. */
public final class MontogramCallConfirmation {
    private static Consent pending;
    private MontogramCallConfirmation() {}

    public static void request(Activity owner,int account,long peer,boolean video,
                               BooleanSupplier callerCurrent,Runnable launch) {
        if(!MontogramConfig.confirmCalls()){launch.run();return;}
        Consent consent=new Consent(owner,account,peer,video,callerCurrent,true,false);
        if(!consent.ownerCurrent()||!consent.serviceCurrent(false)){consent.cancel();return;}
        if(pending!=null)pending.cancel();
        TLRPC.User user=MessagesController.getInstance(account).getUser(peer);
        AlertDialog dialog=new AlertDialog.Builder(owner)
            .setTitle(LocaleController.getString(video?R.string.VideoCallAlertTitle:R.string.CallAlertTitle))
            .setMessage(AndroidUtilities.replaceTags(LocaleController.formatString(
                video?R.string.VideoCallAlert:R.string.CallAlert,UserObject.getUserName(user))))
            .setNegativeButton(LocaleController.getString(R.string.Cancel),null)
            .setPositiveButton(LocaleController.getString(video?R.string.VideoCall:R.string.Call),(d,which)->{
                if(consent.approve())consent.launch(launch);
            }).create();
        consent.observe(dialog);
        try {dialog.show();}catch(RuntimeException ex){consent.cancel();}
    }

    /** The existing replace-call dialog supplies consent; no second Montogram prompt. */
    public static Consent nativeReplacement(Activity owner,int account,long peer,boolean video,
                                             BooleanSupplier callerCurrent) {
        return new Consent(owner,account,peer,video,callerCurrent,MontogramConfig.confirmCalls(),true);
    }

    public static final class Consent implements Application.ActivityLifecycleCallbacks {
        private final WeakReference<Activity> owner;
        private final int account,selectedAtCapture;
        private final long peer,self,callId;
        private final int callState;
        private final boolean video,enabled,replacement;
        private final VoIPService service;
        private final Object incoming; // Identity only; never read or retain a serialized call payload.
        private final BooleanSupplier callerCurrent;
        private final MontogramCallRequest request;
        private WeakReference<AlertDialog> dialog=new WeakReference<>(null);
        private Application application;
        private boolean closed,registered;
        private final Runnable timeout=this::cancel;
        private Consent(Activity owner,int account,long peer,boolean video,BooleanSupplier callerCurrent,boolean enabled,boolean replacement) {
            this.owner=new WeakReference<>(owner);this.account=account;this.peer=peer;this.video=video;
            selectedAtCapture=UserConfig.selectedAccount;
            this.enabled=enabled;this.replacement=replacement;this.callerCurrent=callerCurrent;
            self=account<0||account>=UserConfig.MAX_ACCOUNT_COUNT?0:UserConfig.getInstance(account).getClientUserId();
            service=VoIPService.getSharedInstance();callId=service==null?0:service.getCallID();
            callState=service==null?0:service.getCallState();incoming=VoIPService.callIShouldHavePutIntoIntent;
            request=enabled&&account>=0&&self>0&&peer>0
                ?new MontogramCallRequest(account,self,peer,video,SystemClock.elapsedRealtime()):null;
        }
        public void observe(AlertDialog value) {
            if(!enabled)return;
            dialog=new WeakReference<>(value);
            value.setOnShowListener(d->inheritSecureFlag(value));
            if(value.isShowing())inheritSecureFlag(value);
            if(pending!=null&&pending!=this)pending.cancel();
            pending=this;
            Activity activity=owner.get();
            if(activity==null){cancel();return;}
            application=activity.getApplication();application.registerActivityLifecycleCallbacks(this);registered=true;
            AndroidUtilities.runOnUIThread(timeout,MontogramCallRequest.TIMEOUT_MS);
            value.setOnDismissListener(d->{if(request==null||!request.isApproved())cancel();});
        }
        private boolean ownerCurrent() {
            Activity activity=owner.get();
            if(closed||request==null||activity==null||activity.isFinishing()||activity.isDestroyed()
                    ||org.telegram.messenger.SharedConfig.appLocked
                    ||org.telegram.messenger.SharedConfig.isWaitingForPasscodeEnter
                    ||UserConfig.selectedAccount!=selectedAtCapture)return false;
            UserConfig config=UserConfig.getInstance(account);
            if(!config.isClientActivated()||config.getClientUserId()!=self)return false;
            MessagesController controller=MessagesController.getInstance(account);
            TLRPC.User user=controller.getUser(peer);TLRPC.UserFull full=controller.getUserFull(peer);
            if(user==null||user.id!=peer||user.deleted||user instanceof TLRPC.TL_userEmpty
                    ||controller.isFrozen()||full!=null&&full.phone_calls_private)return false;
            try{return callerCurrent.getAsBoolean();}catch(RuntimeException ex){return false;}
        }
        private void inheritSecureFlag(AlertDialog value) {
            Activity activity=owner.get();
            if(closed||activity==null||activity.getWindow()==null||value.getWindow()==null)return;
            int secure=android.view.WindowManager.LayoutParams.FLAG_SECURE;
            if((activity.getWindow().getAttributes().flags&secure)!=0)value.getWindow().addFlags(secure);
        }
        private boolean serviceCurrent(boolean afterApproval) {
            VoIPService current=VoIPService.getSharedInstance();
            if(VoIPService.callIShouldHavePutIntoIntent!=incoming)return false;
            if(!replacement&&(incoming!=null||service!=null&&(service.getAccount()!=account
                    ||service.getCallerId()!=peer||callState!=VoIPService.STATE_BUSY)))return false;
            if(current!=service)return afterApproval&&current==null;
            return current==null||current.getCallID()==callId&&(afterApproval&&replacement||current.getCallState()==callState);
        }
        public VoIPService serviceToReplace(){return enabled?service:VoIPService.getSharedInstance();}
        public boolean approve() {
            if(!enabled)return true;
            if(request==null){cancel();return false;}
            if(request.isApproved())return false;
            boolean valid=ownerCurrent()&&serviceCurrent(false);
            boolean approved=request!=null&&request.approve(account,
                UserConfig.getInstance(account).getClientUserId(),peer,video,valid,SystemClock.elapsedRealtime());
            if(!approved)cancel();return approved;
        }
        public void launch(Runnable launch) {
            if(!enabled){launch.run();return;}
            if(request==null){cancel();return;}
            boolean valid=ownerCurrent()&&serviceCurrent(true);
            boolean consumed=request!=null&&request.consume(account,
                UserConfig.getInstance(account).getClientUserId(),peer,video,valid,SystemClock.elapsedRealtime());
            close();if(consumed)launch.run();
        }
        public void cancel(){if(request!=null)request.cancel();close();}
        private void close() {
            if(closed)return;closed=true;
            AndroidUtilities.cancelRunOnUIThread(timeout);
            if(pending==this)pending=null;
            if(registered){application.unregisterActivityLifecycleCallbacks(this);registered=false;}
            AlertDialog value=dialog.get();if(value!=null&&value.isShowing())value.dismiss();
        }
        @Override public void onActivityPaused(Activity activity){if(owner.get()==activity)cancel();}
        @Override public void onActivityDestroyed(Activity activity){if(owner.get()==activity)cancel();}
        @Override public void onActivityCreated(Activity a,Bundle b){}
        @Override public void onActivityStarted(Activity a){}
        @Override public void onActivityResumed(Activity a){}
        @Override public void onActivityStopped(Activity a){}
        @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    }
}
