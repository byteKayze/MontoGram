/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;

import android.app.Dialog;
import android.os.Bundle;
import java.util.function.BooleanSupplier;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MontogramBookmarkEligibility;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MontogramBookmarks;
import org.telegram.messenger.MontogramBookmarkData;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.MontogramMaterial;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

/** Local references only; opens the current server message through native access checks. */
public final class MontogramBookmarksActivity extends UniversalFragment {
    private static final int ADD=1, RETAIN=2, CLEAR=3, RETRY=4, RESET=5, FOLDERS=6, FOLDER_ACTIONS=7, FIRST=100;
    private MontogramBookmarks.Snapshot snapshot;
    private MontogramBookmarks.Request request;
    private MontogramBookmarks.Status failure;
    private boolean active, busy, destroyed;
    private int generation;
    private EditText labelInput;
    private String folderFilter;
    private long capturePeer;private int captureMessage,networkRequest;
    private BooleanSupplier captureAllowed;
    private boolean transitionReady;

    public MontogramBookmarksActivity(int account) { setCurrentAccount(account); }
    public MontogramBookmarksActivity(int account,long peer,int message,BooleanSupplier eligible){this(account);capturePeer=peer;captureMessage=message;captureAllowed=eligible;}
    private static String text(int id) { return LocaleController.getString(id); }
    @Override public Theme.ResourcesProvider getResourceProvider() { return MontogramMaterial.scoped(super.getResourceProvider()); }
    @Override protected CharSequence getTitle() { return text(R.string.MontogramBookmarks); }
    private boolean alive(int token) { return active&&!destroyed&&token==generation&&getParentActivity()!=null&&!getParentActivity().isFinishing()&&!getParentActivity().isDestroyed()&&currentAccount==UserConfig.selectedAccount; }
    private void refresh() { if(listView!=null)listView.adapter.update(false); }
    private void load() {
        if(!alive(generation)||busy)return;
        busy=true;failure=null;refresh();final int token=generation;
        request=MontogramBookmarks.load(currentAccount,(status,value)->complete(token,status,value));
    }
    private void complete(int token,MontogramBookmarks.Status status,MontogramBookmarks.Snapshot value) {
        if(!alive(token)){MontogramBookmarks.revoke(value);return;}
        busy=false;request=null;
        if(status==MontogramBookmarks.Status.OK){snapshot=value;failure=null;}
        else if(status!=MontogramBookmarks.Status.INVALID&&status!=MontogramBookmarks.Status.BUSY){snapshot=null;failure=status;}
        refresh();maybeCapture();
        if(status==MontogramBookmarks.Status.INVALID)message(R.string.MontogramBookmarksInvalid);
        else if(status!=MontogramBookmarks.Status.OK&&status!=MontogramBookmarks.Status.BUSY)message(R.string.MontogramBookmarksError);
    }
    private void message(int id) { if(alive(generation))showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(getTitle()).setMessage(text(id)).setPositiveButton(text(R.string.OK),null).create()); }
    @Override public void onResume(){super.onResume();active=true;load();}
    private static void eraseView(View view){if(view instanceof TextView)((TextView)view).setText("");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)eraseView(group.getChildAt(i));}}
    private void drop() {
        folderFilter=null;active=false;++generation;captureAllowed=null;capturePeer=0;captureMessage=0;transitionReady=false;
        if(networkRequest!=0)getConnectionsManager().cancelRequest(networkRequest,true);networkRequest=0;
        if(request!=null)request.cancel();request=null;busy=false;
        MontogramBookmarks.revoke(snapshot);snapshot=null;failure=null;
        if(labelInput!=null)labelInput.setText("");labelInput=null;
        Dialog dialog=getVisibleDialog();if(dialog!=null){eraseView(dialog.getWindow()==null?null:dialog.getWindow().getDecorView());dialog.dismiss();}
        if(listView!=null){eraseView(listView);refresh();listView.getRecycledViewPool().clear();}
    }
    @Override public void onPause(){drop();super.onPause();}
    @Override public void onFragmentDestroy(){destroyed=true;drop();super.onFragmentDestroy();}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter) {
        if(!active||busy){items.add(UItem.asShadow(text(R.string.Loading)));return;}
        List<MontogramBookmarkData.Entry> rows=MontogramBookmarks.entries(snapshot);
        if(rows==null){items.add(UItem.asShadow(text(R.string.MontogramBookmarksError)));items.add(UItem.asButton(RETRY,R.drawable.settings_chat,text(R.string.Retry)));if(failure!=null)items.add(UItem.asButton(RESET,R.drawable.settings_chat,text(R.string.MontogramBookmarksReset)));return;}
        items.add(UItem.asShadow(text(MontogramBookmarks.mode(snapshot)==MontogramBookmarks.Mode.RETAINED?R.string.MontogramBookmarksRetainedInfo:R.string.MontogramBookmarksMemoryInfo)));
        items.add(UItem.asButton(FOLDERS,R.drawable.settings_chat,text(R.string.MontogramBookmarkFolders)+(folderFilter==null?"":" · "+(folderFilter.isEmpty()?text(R.string.MontogramUnfiled):folderFilter))));
        if(folderFilter!=null&&!folderFilter.isEmpty())items.add(UItem.asButton(FOLDER_ACTIONS,R.drawable.settings_chat,text(R.string.MontogramFolderActions)));
        int visible=0;
        for(int i=0;i<rows.size();i++)if(folderFilter==null||folderFilter.equals(rows.get(i).folder)){
            items.add(UItem.asButton(FIRST+i,R.drawable.settings_chat,rows.get(i).label.isEmpty()?rows.get(i).dialogId+" · "+rows.get(i).messageId:rows.get(i).label));visible++;
        }
        if(!rows.isEmpty()&&visible==0)items.add(UItem.asShadow(text(R.string.NoResult)));
        if(rows.isEmpty())items.add(UItem.asShadow(text(R.string.MontogramBookmarksEmpty)));
        if(MontogramBookmarks.mode(snapshot)==MontogramBookmarks.Mode.PROCESS_MEMORY)items.add(UItem.asButton(RETAIN,R.drawable.settings_chat,text(R.string.MontogramBookmarksRetain)));
        items.add(UItem.asButton(CLEAR,R.drawable.settings_chat,text(R.string.MontogramBookmarksReset)));
    }
    @Override protected boolean onLongClick(UItem item,View view,int position,float x,float y){return false;}
    @Override protected void onClick(UItem item,View view,int position,float x,float y) {
        if(!alive(generation)||busy)return;
        if(item.id==RETRY){load();return;}
        if(item.id==RESET){confirmClear(true);return;}
        if(!MontogramBookmarks.current(snapshot)){load();return;}
        if(item.id==FOLDERS){chooseFolder();return;}
        if(item.id==FOLDER_ACTIONS){folderActions();return;}
        if(item.id==RETAIN)confirmRetention();
        else if(item.id==CLEAR)confirmClear(false);
        else {
            List<MontogramBookmarkData.Entry> rows=MontogramBookmarks.entries(snapshot);int index=item.id-FIRST;
            if(rows!=null&&index>=0&&index<rows.size()){
                MontogramBookmarkData.Entry row=rows.get(index);final int token=generation;
                showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(row.label.isEmpty()?getTitle():row.label)
                    .setItems(new CharSequence[]{text(R.string.MontogramBookmarksOpen),text(R.string.MontogramBookmarksRename),text(R.string.Delete),text(R.string.MontogramMoveToFolder)},(d,w)->{
                        if(!alive(token)||!MontogramBookmarks.current(snapshot))return;
                        if(w==0)open(row);else if(w==1)edit(row,null,0,0);else if(w==2){busy=true;refresh();request=MontogramBookmarks.delete(snapshot,row.id,(status,value)->complete(token,status,value));}else if(w==3)editFolder(row.id,row.folder);
                    }).create());
            }
        }
    }
    private java.util.ArrayList<String> folderNames(){
        java.util.TreeSet<String> names=new java.util.TreeSet<>();List<MontogramBookmarkData.Entry> rows=MontogramBookmarks.entries(snapshot);
        if(rows!=null)for(MontogramBookmarkData.Entry row:rows)if(!row.folder.isEmpty())names.add(row.folder);
        return new java.util.ArrayList<>(names);
    }
    private void chooseFolder(){
        final int token=generation;final MontogramBookmarks.Snapshot before=snapshot;
        java.util.ArrayList<String> names=folderNames();CharSequence[] labels=new CharSequence[names.size()+2];labels[0]=text(R.string.MontogramAllFolders);labels[1]=text(R.string.MontogramUnfiled);for(int i=0;i<names.size();i++)labels[i+2]=names.get(i);
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramBookmarkFolders)).setMessage(text(R.string.MontogramBookmarkFoldersInfo))
                .setItems(labels,(dialog,index)->{if(!alive(token)||!MontogramBookmarks.current(before))return;folderFilter=index==0?null:index==1?"":names.get(index-2);refresh();}).create());
    }
    private void editFolder(long id,String previous){
        final int token=generation;final MontogramBookmarks.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        labelInput=new EditText(getParentActivity());EditText input=labelInput;input.setSaveEnabled(false);input.setSingleLine(true);input.setText(previous);input.setHint(text(R.string.MontogramFolderName));input.setTextColor(getThemedColor(Theme.key_dialogTextBlack));input.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramBookmarkData.MAX_FOLDER)});
        LinearLayout fields=new LinearLayout(getParentActivity());fields.setPadding(AndroidUtilities.dp(24),0,AndroidUtilities.dp(24),0);fields.addView(input,new LinearLayout.LayoutParams(-1,-2));
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(id>0?R.string.MontogramMoveToFolder:R.string.MontogramRenameFolder)).setMessage(text(R.string.MontogramBookmarkFoldersInfo)).setView(fields)
                .setNegativeButton(text(R.string.Cancel),null).setPositiveButton(text(R.string.Save),(dialog,button)->{
                    if(!used.compareAndSet(false,true)||!alive(token)||busy||!MontogramBookmarks.current(before))return;
                    String name;try{name=MontogramBookmarkData.canonicalFolderName(input.getText().toString());if(id==0&&name.isEmpty())throw new IllegalArgumentException("Use explicit dissolve");}catch(IllegalArgumentException invalid){message(R.string.MontogramBookmarksInvalid);return;}
                    busy=true;refresh();
                    request=id>0?MontogramBookmarks.move(before,id,name,(status,value)->complete(token,status,value)):MontogramBookmarks.renameFolder(before,previous,name,(status,value)->{if(status==MontogramBookmarks.Status.OK)folderFilter=name;complete(token,status,value);});
                }).create());
    }
    private void folderActions(){
        if(folderFilter==null||folderFilter.isEmpty())return;
        final int token=generation;final MontogramBookmarks.Snapshot before=snapshot;final String folder=folderFilter;
        int count=0;for(MontogramBookmarkData.Entry entry:MontogramBookmarks.entries(before))if(entry.folder.equals(folder))count++;
        final int members=count;
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(folder)
                .setItems(new CharSequence[]{text(R.string.MontogramRenameFolder),text(R.string.MontogramDissolveFolder)},(dialog,index)->{
                    if(!alive(token)||!MontogramBookmarks.current(before))return;
                    if(index==0){editFolder(0,folder);return;}
                    if(index!=1)return;
                    final AtomicBoolean used=new AtomicBoolean();
                    showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramDissolveFolder)).setMessage(String.format(java.util.Locale.getDefault(),text(R.string.MontogramDissolveFolderInfo),members))
                            .setNegativeButton(text(R.string.Cancel),null).setPositiveButton(text(R.string.MontogramDissolveFolder),(d,w)->{if(!used.compareAndSet(false,true)||!alive(token)||busy||!MontogramBookmarks.current(before))return;busy=true;refresh();request=MontogramBookmarks.renameFolder(before,folder,"",(status,value)->{if(status==MontogramBookmarks.Status.OK)folderFilter="";complete(token,status,value);});}).create());
                }).create());
    }
    @Override public void onTransitionAnimationEnd(boolean open,boolean backward){super.onTransitionAnimationEnd(open,backward);if(open){transitionReady=true;maybeCapture();}}
    private void maybeCapture(){
        if(!transitionReady||busy||!alive(generation)||captureAllowed==null||!MontogramBookmarks.current(snapshot))return;
        BooleanSupplier allowed=captureAllowed;long peer=capturePeer;int message=captureMessage;
        captureAllowed=null;capturePeer=0;captureMessage=0;
        if(!allowed.getAsBoolean()){message(R.string.MontogramBookmarksUnavailable);return;}
        edit(null,allowed,peer,message);
    }
    private void edit(MontogramBookmarkData.Entry row,BooleanSupplier capture,long peer,int messageId){
        final int token=generation;final MontogramBookmarks.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        labelInput=new EditText(getParentActivity());final EditText label=labelInput;
        label.setSingleLine(true);label.setHint(text(R.string.MontogramBookmarksLabel));label.setTextColor(getThemedColor(Theme.key_dialogTextBlack));label.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        label.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramBookmarkData.MAX_LABEL)});if(row!=null)label.setText(row.label);
        LinearLayout fields=new LinearLayout(getParentActivity());fields.setPadding(AndroidUtilities.dp(24),0,AndroidUtilities.dp(24),0);fields.addView(label,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(getTitle()).setView(fields).setNegativeButton(text(R.string.Cancel),(d,w)->label.setText(""))
            .setPositiveButton(text(R.string.Save),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||!MontogramBookmarks.current(before)){label.setText("");return;}
                if(capture!=null&&!capture.getAsBoolean()){label.setText("");message(R.string.MontogramBookmarksUnavailable);return;}
                String name=label.getText().toString();label.setText("");busy=true;refresh();
                request=row==null?MontogramBookmarks.save(before,peer,messageId,name,(status,value)->complete(token,status,value)):MontogramBookmarks.rename(before,row.id,name,(status,value)->complete(token,status,value));
            }).create();
        if(showDialog(dialog,d->{label.setText("");if(labelInput==label)labelInput=null;})==null){label.setText("");labelInput=null;}
    }
    private boolean openAlive(int token,MontogramBookmarks.Snapshot before){return alive(token)&&snapshot==before&&MontogramBookmarks.current(before);}
    private void unavailable(int token,MontogramBookmarks.Snapshot before){if(!openAlive(token,before))return;busy=false;networkRequest=0;refresh();message(R.string.MontogramBookmarksUnavailable);}
    private void open(MontogramBookmarkData.Entry entry){
        final int token=generation;final MontogramBookmarks.Snapshot before=snapshot;
        if(!openAlive(token,before))return;busy=true;refresh();
        if(entry.dialogId>0){
            TLRPC.User user=getMessagesController().getUser(entry.dialogId);if(user==null){unavailable(token,before);return;}
            TLRPC.TL_users_getFullUser query=new TLRPC.TL_users_getFullUser();query.id=getMessagesController().getInputUser(user);
            networkRequest=getConnectionsManager().sendRequest(query,(response,error)->AndroidUtilities.runOnUIThread(()->{
                if(!openAlive(token,before))return;networkRequest=0;
                if(error!=null||!(response instanceof TLRPC.TL_users_userFull)){unavailable(token,before);return;}
                TLRPC.UserFull full=((TLRPC.TL_users_userFull)response).full_user;
                if(full==null||full.id!=entry.dialogId||full.noforwards_peer_enabled||full.noforwards_my_enabled){unavailable(token,before);return;}
                fetch(entry,token,before,full);
            }));getConnectionsManager().bindRequestToGuid(networkRequest,classGuid);
        }else fetch(entry,token,before,null);
    }
    private void fetch(MontogramBookmarkData.Entry entry,int token,MontogramBookmarks.Snapshot before,TLRPC.UserFull full){
        if(!openAlive(token,before))return;
        TLObject query;
        if(entry.dialogId<0){
            TLRPC.Chat peer=getMessagesController().getChat(-entry.dialogId);if(peer==null){unavailable(token,before);return;}
            if(ChatObject.isChannel(peer)){TLRPC.TL_channels_getMessages request=new TLRPC.TL_channels_getMessages();request.channel=getMessagesController().getInputChannel(-entry.dialogId);request.id.add(entry.messageId);query=request;}
            else{TLRPC.TL_messages_getMessages request=new TLRPC.TL_messages_getMessages();request.id.add(entry.messageId);query=request;}
        }else{TLRPC.TL_messages_getMessages request=new TLRPC.TL_messages_getMessages();request.id.add(entry.messageId);query=request;}
        networkRequest=getConnectionsManager().sendRequest(query,(response,error)->AndroidUtilities.runOnUIThread(()->{
            if(!openAlive(token,before))return;networkRequest=0;
            if(error!=null||!(response instanceof TLRPC.messages_Messages)){unavailable(token,before);return;}
            TLRPC.messages_Messages result=(TLRPC.messages_Messages)response;TLRPC.Message exact=null;TLRPC.Chat chat=null;
            for(TLRPC.Message message:result.messages)if(message.id==entry.messageId&&MessageObject.getDialogId(message)==entry.dialogId){if(exact!=null){unavailable(token,before);return;}exact=message;}
            if(entry.dialogId<0)for(TLRPC.Chat candidate:result.chats)if(candidate.id==-entry.dialogId)chat=candidate;
            if(exact==null){unavailable(token,before);return;}
            MessageObject object=new MessageObject(currentAccount,exact,false,false);
            if(!MontogramBookmarkEligibility.permits(object,currentAccount,entry.dialogId,entry.messageId,full,chat)){unavailable(token,before);return;}
            // A newly observed cached protection also denies, even if the earlier full-user fetch allowed.
            if(getMessagesController().isPeerNoForwards(entry.dialogId)){unavailable(token,before);return;}
            getMessagesController().putUsers(result.users,false);getMessagesController().putChats(result.chats,false);
            Bundle args=new Bundle();if(entry.dialogId>0)args.putLong("user_id",entry.dialogId);else args.putLong("chat_id",-entry.dialogId);args.putInt("message_id",entry.messageId);
            if(exact.reply_to!=null&&exact.reply_to.forum_topic)args.putLong("topic_id",exact.reply_to.reply_to_top_id!=0?exact.reply_to.reply_to_top_id:exact.reply_to.reply_to_msg_id);
            if(!openAlive(token,before)||!getMessagesController().checkCanOpenChat(args,this)){busy=false;refresh();return;}
            busy=false;refresh();ChatActivity target=new ChatActivity(args);target.setCurrentAccount(currentAccount);presentFragment(target);
        }));getConnectionsManager().bindRequestToGuid(networkRequest,classGuid);
    }
    private void confirmRetention() {
        final int token=generation;final MontogramBookmarks.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramBookmarksRetain))
            .setMessage(text(R.string.MontogramBookmarksRetainConfirm)).setNegativeButton(text(R.string.Cancel),null)
            .setPositiveButton(text(R.string.MontogramBookmarksRetain),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||!MontogramBookmarks.current(before))return;
                busy=true;refresh();request=MontogramBookmarks.retainAfterConfirmedUserAction(before,(s,v)->complete(token,s,v));
            }).create());
    }
    private void confirmClear(boolean recover) {
        final int token=generation;final MontogramBookmarks.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramBookmarksReset))
            .setMessage(text(R.string.MontogramBookmarksResetConfirm)).setNegativeButton(text(R.string.Cancel),null)
            .setPositiveButton(text(R.string.Delete),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||(!recover&&!MontogramBookmarks.current(before)))return;
                busy=true;refresh();request=recover?MontogramBookmarks.resetRetained(currentAccount,(s,v)->complete(token,s,v)):MontogramBookmarks.clear(before,(s,v)->complete(token,s,v));
            }).create());
    }
}
