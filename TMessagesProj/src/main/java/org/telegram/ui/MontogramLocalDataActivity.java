/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MontogramBookmarks;
import org.telegram.messenger.MontogramLocalExport;
import org.telegram.messenger.MontogramNotes;
import org.telegram.messenger.MontogramQuickReplies;
import org.telegram.messenger.MontogramQuickReplyData;
import org.telegram.messenger.MontogramSafIO;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.MontogramMaterial;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

/** Explicit local-data stats and consented document transfers; never reads chat bodies. */
public final class MontogramLocalDataActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {
    private static final int EXPORT_NOTES=1,EXPORT_BOOKMARKS=2,EXPORT_REPLIES=3,IMPORT_REPLIES=4,REFRESH=5,MANAGE_NOTES=6,MANAGE_BOOKMARKS=7,MANAGE_REPLIES=8,PICKER=7520;
    private boolean active,destroyed,transferBusy,pickerInFlight;
    private int generation,pickerGeneration,pickerOperation;
    private long self,pickerSelf;
    private Uri returnedUri; // One-shot result-to-first-resume handoff only; never saved/persisted.
    private int returnedGeneration;
    private MontogramNotes.Snapshot notes;
    private MontogramBookmarks.Snapshot bookmarks;
    private MontogramQuickReplies.Snapshot replies;
    private MontogramNotes.Request notesRequest;
    private MontogramBookmarks.Request bookmarksRequest;
    private MontogramQuickReplies.Request repliesRequest;
    private MontogramSafIO.Handle transfer;
    private String notesStats,bookmarkStats,replyStats;
    private Grant grant;
    private MontogramQuickReplyData incoming;
    private String incomingDigest;
    private MontogramQuickReplies.Snapshot importTarget;
    private long importRevision;
    private AlertDialog privateDialog;
    private View focusView;
    private ViewTreeObserver.OnWindowFocusChangeListener focusListener;
    private static final class Grant { Uri uri; final int operation,generation;final long self;Grant(Uri uri,int op,int generation,long self){this.uri=uri;operation=op;this.generation=generation;this.self=self;} }
    public MontogramLocalDataActivity(int account){setCurrentAccount(account);}
    private static String text(int id){return LocaleController.getString(id);}
    @Override protected CharSequence getTitle(){return text(R.string.MontogramLocalData);}
    @Override public Theme.ResourcesProvider getResourceProvider(){return MontogramMaterial.scoped(super.getResourceProvider());}
    @Override public boolean onFragmentCreate(){if(!super.onFragmentCreate())return false;NotificationCenter.getGlobalInstance().addObserver(this,NotificationCenter.activeAccountChanged);NotificationCenter.getInstance(currentAccount).addObserver(this,NotificationCenter.appDidLogout);return true;}
    @Override public void didReceivedNotification(int id,int account,Object... args){drop(true);}
    private boolean alive(int token){return active&&!destroyed&&generation==token&&currentAccount==UserConfig.selectedAccount&&self>0&&self==UserConfig.getInstance(currentAccount).getClientUserId()&&UserConfig.getInstance(currentAccount).isClientActivated()&&!SharedConfig.appLocked&&!SharedConfig.isWaitingForPasscodeEnter&&getParentActivity()!=null&&!getParentActivity().isFinishing()&&!getParentActivity().isDestroyed();}
    private void refresh(){if(listView!=null)listView.adapter.update(false);}
    private String stats(int count,int bytes){return String.format(Locale.getDefault(),text(R.string.MontogramLocalDataStats),count,bytes);}
    @Override public void onResume(){super.onResume();active=true;self=UserConfig.getInstance(currentAccount).getClientUserId();if(alive(generation))load();else returnedUri=null;}
    private void load(){
        final int token=generation;
        notesRequest=MontogramNotes.load(currentAccount,(status,value)->{
            if(!alive(token)){MontogramNotes.revoke(value);return;}notesRequest=null;notes=status==MontogramNotes.Status.OK?value:null;notesStats=null;
            try{if(MontogramNotes.current(notes))notesStats=stats(MontogramNotes.entries(notes).size(),MontogramNotes.serializedBytes(notes));}catch(Exception ignored){}
            refresh();consumeReturned();
        });
        bookmarksRequest=MontogramBookmarks.load(currentAccount,(status,value)->{
            if(!alive(token)){MontogramBookmarks.revoke(value);return;}bookmarksRequest=null;bookmarks=status==MontogramBookmarks.Status.OK?value:null;bookmarkStats=null;
            try{if(MontogramBookmarks.current(bookmarks))bookmarkStats=stats(MontogramBookmarks.entries(bookmarks).size(),MontogramBookmarks.serializedBytes(bookmarks));}catch(Exception ignored){}
            refresh();consumeReturned();
        });
        repliesRequest=MontogramQuickReplies.load(currentAccount,(status,value)->{
            if(!alive(token)){MontogramQuickReplies.revoke(value);return;}repliesRequest=null;replies=status==MontogramQuickReplies.Status.OK?value:null;replyStats=null;
            try{if(MontogramQuickReplies.current(replies))replyStats=stats(MontogramQuickReplies.entries(replies).size(),MontogramQuickReplies.serializedBytes(replies));}catch(Exception ignored){}
            refresh();consumeReturned();
        });
    }
    private static void erase(View view){if(view instanceof TextView)((TextView)view).setText("");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)erase(group.getChildAt(i));}}
    private void detachFocus(){if(focusView!=null&&focusListener!=null&&focusView.getViewTreeObserver().isAlive())focusView.getViewTreeObserver().removeOnWindowFocusChangeListener(focusListener);focusView=null;focusListener=null;}
    private void clearPrivate(){
        if(grant!=null)grant.uri=null;grant=null;incoming=null;incomingDigest=null;importTarget=null;importRevision=-1;
        detachFocus();AlertDialog dialog=privateDialog;privateDialog=null;
        if(dialog!=null){if(dialog.getWindow()!=null)erase(dialog.getWindow().getDecorView());AndroidUtilities.runOnUIThread(dialog::dismiss);}
    }
    private void drop(boolean invalidatePicker){
        active=false;++generation;self=0;returnedUri=null;clearPrivate();
        if(transfer!=null)transfer.cancel();transfer=null;transferBusy=false;
        if(notesRequest!=null)notesRequest.cancel();if(bookmarksRequest!=null)bookmarksRequest.cancel();if(repliesRequest!=null)repliesRequest.cancel();notesRequest=null;bookmarksRequest=null;repliesRequest=null;
        MontogramNotes.revoke(notes);MontogramBookmarks.revoke(bookmarks);MontogramQuickReplies.revoke(replies);notes=null;bookmarks=null;replies=null;notesStats=null;bookmarkStats=null;replyStats=null;
        if(invalidatePicker){pickerInFlight=false;pickerOperation=0;pickerSelf=0;++pickerGeneration;}
        if(listView!=null){erase(listView);refresh();listView.getRecycledViewPool().clear();}
    }
    @Override public void onPause(){drop(!pickerInFlight);super.onPause();}
    @Override public void onFragmentDestroy(){destroyed=true;drop(true);NotificationCenter.getGlobalInstance().removeObserver(this,NotificationCenter.activeAccountChanged);NotificationCenter.getInstance(currentAccount).removeObserver(this,NotificationCenter.appDidLogout);super.onFragmentDestroy();}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter){
        if(!alive(generation)){items.add(UItem.asShadow(text(R.string.Loading)));return;}
        items.add(UItem.asShadow(text(R.string.MontogramLocalDataInfo)));
        items.add(UItem.asShadow(text(R.string.MontogramNotes)+"\n"+(notesStats==null||!MontogramNotes.current(notes)?text(R.string.MontogramLocalDataUnavailable):notesStats)));
        items.add(UItem.asShadow(text(R.string.MontogramBookmarks)+"\n"+(bookmarkStats==null||!MontogramBookmarks.current(bookmarks)?text(R.string.MontogramLocalDataUnavailable):bookmarkStats)));
        items.add(UItem.asShadow(text(R.string.MontogramQuickReplies)+"\n"+(replyStats==null||!MontogramQuickReplies.current(replies)?text(R.string.MontogramLocalDataUnavailable):replyStats)));
        if(transferBusy){items.add(UItem.asShadow(text(R.string.Loading)));return;}
        if(MontogramNotes.current(notes))items.add(UItem.asButton(EXPORT_NOTES,R.drawable.settings_chat,text(R.string.MontogramExportNotes)));
        if(MontogramBookmarks.current(bookmarks))items.add(UItem.asButton(EXPORT_BOOKMARKS,R.drawable.settings_chat,text(R.string.MontogramExportBookmarkLabels)));
        if(MontogramQuickReplies.current(replies)){items.add(UItem.asButton(EXPORT_REPLIES,R.drawable.settings_chat,text(R.string.MontogramExportTemplates)));items.add(UItem.asButton(IMPORT_REPLIES,R.drawable.settings_chat,text(R.string.MontogramImportTemplates)));}
        items.add(UItem.asButton(REFRESH,R.drawable.settings_chat,text(R.string.MontogramDataRefresh)));
        items.add(UItem.asButton(MANAGE_NOTES,R.drawable.settings_chat,text(R.string.MontogramNotes)));
        items.add(UItem.asButton(MANAGE_BOOKMARKS,R.drawable.settings_chat,text(R.string.MontogramBookmarks)));
        items.add(UItem.asButton(MANAGE_REPLIES,R.drawable.settings_chat,text(R.string.MontogramQuickReplies)));
    }
    @Override protected boolean onLongClick(UItem item,View view,int position,float x,float y){return false;}
    @Override protected void onClick(UItem item,View view,int position,float x,float y){
        if(!alive(generation)||transferBusy||pickerInFlight)return;
        if(item.id==REFRESH){drop(true);active=true;self=UserConfig.getInstance(currentAccount).getClientUserId();load();return;}
        if(item.id==MANAGE_NOTES){presentFragment(new MontogramNotesActivity(currentAccount));return;}
        if(item.id==MANAGE_BOOKMARKS){presentFragment(new MontogramBookmarksActivity(currentAccount));return;}
        if(item.id==MANAGE_REPLIES){presentFragment(new MontogramQuickRepliesActivity(currentAccount));return;}
        if(item.id<EXPORT_NOTES||item.id>IMPORT_REPLIES||!sourceCurrent(item.id))return;
        if(!MontogramSafIO.isAvailable()||MontogramSafIO.isBusy()){message(R.string.MontogramDataBusy);return;}
        if(item.id==IMPORT_REPLIES){launchPicker(item.id);return;}
        final int operation=item.id;final Object before=sourceSnapshot(operation);final int token=generation;
        String disclosure=String.format(Locale.getDefault(),text(disclosure(operation)),recordCount(operation));
        showPrivate(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(title(operation))).setMessage(disclosure)
                .setNegativeButton(text(R.string.Cancel),null).setPositiveButton(text(R.string.MontogramChooseDocument),(dialog,button)->{if(alive(token)&&before==sourceSnapshot(operation)&&sourceCurrent(operation))launchPicker(operation);}).create());
    }
    private int title(int operation){return operation==EXPORT_NOTES?R.string.MontogramExportNotes:operation==EXPORT_BOOKMARKS?R.string.MontogramExportBookmarkLabels:operation==EXPORT_REPLIES?R.string.MontogramExportTemplates:R.string.MontogramImportTemplates;}
    private int disclosure(int operation){return operation==EXPORT_NOTES?R.string.MontogramExportNotesDisclosure:operation==EXPORT_BOOKMARKS?R.string.MontogramExportBookmarksDisclosure:R.string.MontogramExportTemplatesDisclosure;}
    private Object sourceSnapshot(int operation){return operation==EXPORT_NOTES?notes:operation==EXPORT_BOOKMARKS?bookmarks:replies;}
    private boolean sourceCurrent(int operation){return operation==EXPORT_NOTES?MontogramNotes.current(notes):operation==EXPORT_BOOKMARKS?MontogramBookmarks.current(bookmarks):MontogramQuickReplies.current(replies);}
    private int recordCount(int operation){return operation==EXPORT_NOTES?MontogramNotes.entries(notes).size():operation==EXPORT_BOOKMARKS?MontogramBookmarks.entries(bookmarks).size():MontogramQuickReplies.entries(replies).size();}
    private void launchPicker(int operation){
        if(!alive(generation)||!sourceCurrent(operation))return;
        pickerOperation=operation;pickerSelf=self;pickerInFlight=true;++pickerGeneration;returnedUri=null;
        Intent intent=new Intent(operation==IMPORT_REPLIES?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(operation==IMPORT_REPLIES||operation==EXPORT_REPLIES?"application/octet-stream":"application/json");
        if(operation!=IMPORT_REPLIES)intent.putExtra(Intent.EXTRA_TITLE,operation==EXPORT_NOTES?"Montogram-notes.json":operation==EXPORT_BOOKMARKS?"Montogram-bookmark-labels.json":"Montogram-quick-replies.mqr");
        try{startActivityForResult(intent,PICKER);}catch(Exception unavailable){pickerInFlight=false;pickerOperation=0;message(R.string.MontogramLocalDataUnavailable);}
    }
    @Override public void onActivityResultFragment(int request,int result,Intent data){
        if(request!=PICKER){super.onActivityResultFragment(request,result,data);return;}
        if(!pickerInFlight||destroyed)return;pickerInFlight=false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null||!"content".equals(data.getData().getScheme())||currentAccount!=UserConfig.selectedAccount||pickerSelf!=UserConfig.getInstance(currentAccount).getClientUserId()||SharedConfig.appLocked||SharedConfig.isWaitingForPasscodeEnter){pickerOperation=0;returnedUri=null;return;}
        returnedUri=data.getData();returnedGeneration=pickerGeneration;
        consumeReturned(); // If result precedes onResume, consume only after fresh owner acquisition.
    }
    private void consumeReturned(){
        if(returnedUri==null||!alive(generation)||returnedGeneration!=pickerGeneration||self!=pickerSelf||!sourceCurrent(pickerOperation))return;
        grant=new Grant(returnedUri,pickerOperation,generation,self);returnedUri=null;pickerOperation=0;
        if(grant.operation==IMPORT_REPLIES){readImport();return;}
        final Grant accepted=grant;final Object before=sourceSnapshot(accepted.operation);
        String disclosure=String.format(Locale.getDefault(),text(disclosure(accepted.operation)),recordCount(accepted.operation))+"\n\n"+text(R.string.MontogramFinalExportInfo);
        showPrivate(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(title(accepted.operation))).setMessage(disclosure)
                .setNegativeButton(text(R.string.Cancel),(dialog,button)->clearPrivate())
                .setPositiveButton(text(R.string.MontogramExportNow),(dialog,button)->{
                    if(valid(accepted)&&before==sourceSnapshot(accepted.operation)&&sourceCurrent(accepted.operation))writeExport(accepted);else clearPrivate();
                }).create());
    }
    private boolean valid(Grant accepted){return grant==accepted&&accepted.uri!=null&&accepted.generation==generation&&accepted.self==self&&alive(generation);}
    private void writeExport(Grant accepted){
        byte[] bytes=null;
        try{
            bytes=accepted.operation==EXPORT_NOTES?MontogramNotes.exportPortable(notes):accepted.operation==EXPORT_BOOKMARKS?MontogramBookmarks.exportPortable(bookmarks):MontogramQuickReplies.exportPortable(replies);
            final int token=generation;final AtomicBoolean completed=new AtomicBoolean();transferBusy=true;
            MontogramSafIO.Handle handle=MontogramSafIO.transfer(accepted.uri,bytes,262144,(status,ignored)->{
                completed.set(true);if(ignored!=null)Arrays.fill(ignored,(byte)0);
                if(!alive(token))return;transfer=null;transferBusy=false;refresh();message(status==MontogramSafIO.Status.SUCCESS?R.string.MontogramExportDone:R.string.MontogramLocalDataUnavailable);
            });
            transfer=completed.get()?null:handle;
        }catch(Exception invalid){message(R.string.MontogramLocalDataUnavailable);}
        finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);clearPrivate();refresh();}
    }
    private void readImport(){
        final Grant accepted=grant;final MontogramQuickReplies.Snapshot before=replies;final int token=generation;final AtomicBoolean completed=new AtomicBoolean();transferBusy=true;
        MontogramSafIO.Handle handle=MontogramSafIO.transfer(accepted.uri,null,MontogramQuickReplyData.MAX_BYTES,(status,bytes)->{
            completed.set(true);
            if(alive(token)){transfer=null;transferBusy=false;}
            try{
                if(!alive(token)||before!=replies||!MontogramQuickReplies.current(before))return;
                if(status!=MontogramSafIO.Status.SUCCESS||bytes==null){message(R.string.MontogramLocalDataUnavailable);return;}
                incoming=MontogramQuickReplyData.decode(bytes);byte[] canonical=incoming.encode();
                try{incomingDigest=MontogramLocalExport.digest(canonical);}finally{Arrays.fill(canonical,(byte)0);}
                importTarget=before;importRevision=MontogramQuickReplies.revision(before);showImportPreview();
            }catch(Exception invalid){incoming=null;incomingDigest=null;importTarget=null;message(R.string.MontogramLocalDataUnavailable);}
            finally{if(bytes!=null)Arrays.fill(bytes,(byte)0);refresh();}
        });
        transfer=completed.get()?null:handle;accepted.uri=null;grant=null;refresh();
    }
    private void showImportPreview(){
        int before=MontogramQuickReplies.entries(importTarget).size(),after=MontogramQuickReplies.previewMergeCount(importTarget,incoming);
        StringBuilder summary=new StringBuilder(String.format(Locale.getDefault(),text(R.string.MontogramImportPreview),before,after,after-before));
        for(MontogramQuickReplyData.Reply reply:incoming.replies)summary.append("\n• ").append(reply.label);
        showPrivate(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramImportTemplates)).setMessage(summary)
                .setNegativeButton(text(R.string.Cancel),(dialog,button)->clearPrivate())
                .setPositiveButton(text(R.string.MontogramMergeTemplates),(dialog,button)->{
                    if(!alive(generation)||incoming==null||incomingDigest==null||importTarget!=replies||!MontogramQuickReplies.current(importTarget)||MontogramQuickReplies.revision(importTarget)!=importRevision){clearPrivate();return;}
                    MontogramQuickReplyData value=incoming;String digest=incomingDigest;long revision=importRevision;MontogramQuickReplies.Snapshot target=importTarget;final int token=generation;clearPrivate();transferBusy=true;
                    repliesRequest=MontogramQuickReplies.mergeImported(target,value,digest,revision,(status,fresh)->{
                        if(!alive(token)){MontogramQuickReplies.revoke(fresh);return;}repliesRequest=null;transferBusy=false;
                        if(status==MontogramQuickReplies.Status.OK){replies=fresh;try{replyStats=stats(MontogramQuickReplies.entries(fresh).size(),MontogramQuickReplies.serializedBytes(fresh));}catch(Exception ignored){replyStats=null;}}
                        else message(R.string.MontogramLocalDataUnavailable);refresh();
                    });
                }).create());
    }
    private void showPrivate(AlertDialog dialog){
        detachFocus();privateDialog=dialog;final boolean[] focused={false};
        dialog.setOnShowListener(shown->{if(privateDialog!=dialog)return;focusView=dialog.getWindow().getDecorView();focusListener=value->{if(value)focused[0]=true;else if(focused[0]&&privateDialog==dialog)clearPrivate();};focusView.getViewTreeObserver().addOnWindowFocusChangeListener(focusListener);focused[0]=focusView.hasWindowFocus();});
        if(showDialog(dialog,dismissed->{if(privateDialog==dialog){privateDialog=null;detachFocus();if(grant!=null)grant.uri=null;grant=null;incoming=null;incomingDigest=null;importTarget=null;}})==null)clearPrivate();
    }
    private void message(int resource){if(alive(generation))showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(getTitle()).setMessage(text(resource)).setPositiveButton(text(R.string.OK),null).create());}
}
