/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;

import android.app.Dialog;
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
import org.telegram.messenger.MontogramQuickReplies;
import org.telegram.messenger.MontogramQuickReplyData;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.MontogramMaterial;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

/** User-authored local templates. Never reads messages or sends them. */
public final class MontogramQuickRepliesActivity extends UniversalFragment {
    private static final int ADD=1, RETAIN=2, CLEAR=3, RETRY=4, RESET=5, FIRST=100;
    private MontogramQuickReplies.Snapshot snapshot;
    private MontogramQuickReplies.Request request;
    private MontogramQuickReplies.Status failure;
    private boolean active, busy, destroyed;
    private int generation;
    private EditText labelInput, textInput;

    public MontogramQuickRepliesActivity(int account) { setCurrentAccount(account); }
    private static String text(int id) { return LocaleController.getString(id); }
    @Override public Theme.ResourcesProvider getResourceProvider() { return MontogramMaterial.scoped(super.getResourceProvider()); }
    @Override protected CharSequence getTitle() { return text(R.string.MontogramQuickReplies); }
    private boolean alive(int token) { return active&&!destroyed&&token==generation&&getParentActivity()!=null&&!getParentActivity().isFinishing()&&!getParentActivity().isDestroyed()&&currentAccount==UserConfig.selectedAccount; }
    private void refresh() { if(listView!=null)listView.adapter.update(false); }
    private void load() {
        if(!alive(generation)||busy)return;
        busy=true;failure=null;refresh();final int token=generation;
        request=MontogramQuickReplies.load(currentAccount,(status,value)->complete(token,status,value));
    }
    private void complete(int token,MontogramQuickReplies.Status status,MontogramQuickReplies.Snapshot value) {
        if(!alive(token)){MontogramQuickReplies.revoke(value);return;}
        busy=false;request=null;
        if(status==MontogramQuickReplies.Status.OK){snapshot=value;failure=null;}
        else if(status!=MontogramQuickReplies.Status.INVALID&&status!=MontogramQuickReplies.Status.BUSY){snapshot=null;failure=status;}
        refresh();
        if(status==MontogramQuickReplies.Status.INVALID)message(R.string.MontogramQuickRepliesInvalid);
        else if(status!=MontogramQuickReplies.Status.OK&&status!=MontogramQuickReplies.Status.BUSY)message(R.string.MontogramQuickRepliesError);
    }
    private void message(int id) { if(alive(generation))showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(getTitle()).setMessage(text(id)).setPositiveButton(text(R.string.OK),null).create()); }
    @Override public void onResume(){super.onResume();active=true;load();}
    private static void eraseView(View view){if(view instanceof TextView)((TextView)view).setText("");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)eraseView(group.getChildAt(i));}}
    private void drop() {
        active=false;++generation;
        if(request!=null)request.cancel();request=null;busy=false;
        MontogramQuickReplies.revoke(snapshot);snapshot=null;failure=null;
        if(labelInput!=null)labelInput.setText("");if(textInput!=null)textInput.setText("");labelInput=null;textInput=null;
        Dialog dialog=getVisibleDialog();if(dialog!=null){eraseView(dialog.getWindow()==null?null:dialog.getWindow().getDecorView());dialog.dismiss();}
        if(listView!=null){eraseView(listView);refresh();listView.getRecycledViewPool().clear();}
    }
    @Override public void onPause(){drop();super.onPause();}
    @Override public void onFragmentDestroy(){destroyed=true;drop();super.onFragmentDestroy();}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter) {
        if(!active||busy){items.add(UItem.asShadow(text(R.string.Loading)));return;}
        List<MontogramQuickReplyData.Reply> rows=MontogramQuickReplies.entries(snapshot);
        if(rows==null){items.add(UItem.asShadow(text(R.string.MontogramQuickRepliesError)));items.add(UItem.asButton(RETRY,R.drawable.settings_chat,text(R.string.Retry)));if(failure!=null)items.add(UItem.asButton(RESET,R.drawable.settings_chat,text(R.string.MontogramQuickRepliesReset)));return;}
        items.add(UItem.asShadow(text(MontogramQuickReplies.mode(snapshot)==MontogramQuickReplies.Mode.RETAINED?R.string.MontogramQuickRepliesRetainedInfo:R.string.MontogramQuickRepliesMemoryInfo)));
        for(int i=0;i<rows.size();i++)items.add(UItem.asButton(FIRST+i,R.drawable.settings_chat,rows.get(i).label));
        items.add(UItem.asButton(ADD,R.drawable.settings_chat,text(R.string.MontogramQuickRepliesAdd)));
        if(MontogramQuickReplies.mode(snapshot)==MontogramQuickReplies.Mode.PROCESS_MEMORY)items.add(UItem.asButton(RETAIN,R.drawable.settings_chat,text(R.string.MontogramQuickRepliesRetain)));
        items.add(UItem.asButton(CLEAR,R.drawable.settings_chat,text(R.string.MontogramQuickRepliesReset)));
    }
    @Override protected boolean onLongClick(UItem item,View view,int position,float x,float y){return false;}
    @Override protected void onClick(UItem item,View view,int position,float x,float y) {
        if(!alive(generation)||busy)return;
        if(item.id==RETRY){load();return;}
        if(item.id==RESET){confirmClear(true);return;}
        if(!MontogramQuickReplies.current(snapshot)){load();return;}
        if(item.id==ADD)edit(null);
        else if(item.id==RETAIN)confirmRetention();
        else if(item.id==CLEAR)confirmClear(false);
        else {
            List<MontogramQuickReplyData.Reply> rows=MontogramQuickReplies.entries(snapshot);int index=item.id-FIRST;
            if(rows!=null&&index>=0&&index<rows.size())edit(rows.get(index));
        }
    }
    private void edit(MontogramQuickReplyData.Reply row) {
        final int token=generation;final MontogramQuickReplies.Snapshot before=snapshot;final long id=row==null?0:row.id;
        LinearLayout fields=new LinearLayout(getParentActivity());fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(AndroidUtilities.dp(24),0,AndroidUtilities.dp(24),0);
        labelInput=new EditText(getParentActivity());textInput=new EditText(getParentActivity());
        labelInput.setSingleLine(true);labelInput.setHint(text(R.string.MontogramQuickRepliesLabel));textInput.setHint(text(R.string.MontogramQuickRepliesText));textInput.setMinLines(3);textInput.setMaxLines(8);
        labelInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));textInput.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        labelInput.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));textInput.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
        labelInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramQuickReplyData.MAX_LABEL)});textInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramQuickReplyData.MAX_TEXT)});
        if(row!=null){labelInput.setText(row.label);textInput.setText(row.text);}
        fields.addView(labelInput,new LinearLayout.LayoutParams(-1,-2));fields.addView(textInput,new LinearLayout.LayoutParams(-1,-2));
        final EditText label=labelInput,body=textInput;final AtomicBoolean used=new AtomicBoolean();
        AlertDialog.Builder builder=new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(getTitle()).setView(fields)
            .setNegativeButton(text(R.string.Cancel),(d,w)->{label.setText("");body.setText("");})
            .setPositiveButton(text(R.string.Save),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||!MontogramQuickReplies.current(before)){label.setText("");body.setText("");return;}
                String name=label.getText().toString(),value=body.getText().toString();label.setText("");body.setText("");busy=true;refresh();
                request=MontogramQuickReplies.save(before,id,name,value,(s,v)->complete(token,s,v));
            });
        if(row!=null)builder.setNeutralButton(text(R.string.Delete),(d,w)->{label.setText("");body.setText("");if(used.compareAndSet(false,true)&&alive(token)&&MontogramQuickReplies.current(before)){busy=true;refresh();request=MontogramQuickReplies.delete(before,id,(s,v)->complete(token,s,v));}});
        AlertDialog dialog=builder.create();if(showDialog(dialog,d->{label.setText("");body.setText("");if(labelInput==label)labelInput=null;if(textInput==body)textInput=null;})==null){label.setText("");body.setText("");labelInput=null;textInput=null;}
    }
    private void confirmRetention() {
        final int token=generation;final MontogramQuickReplies.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramQuickRepliesRetain))
            .setMessage(text(R.string.MontogramQuickRepliesRetainConfirm)).setNegativeButton(text(R.string.Cancel),null)
            .setPositiveButton(text(R.string.MontogramQuickRepliesRetain),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||!MontogramQuickReplies.current(before))return;
                busy=true;refresh();request=MontogramQuickReplies.retainAfterConfirmedUserAction(before,(s,v)->complete(token,s,v));
            }).create());
    }
    private void confirmClear(boolean recover) {
        final int token=generation;final MontogramQuickReplies.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramQuickRepliesReset))
            .setMessage(text(R.string.MontogramQuickRepliesResetConfirm)).setNegativeButton(text(R.string.Cancel),null)
            .setPositiveButton(text(R.string.Delete),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||(!recover&&!MontogramQuickReplies.current(before)))return;
                busy=true;refresh();request=recover?MontogramQuickReplies.resetRetained(currentAccount,(s,v)->complete(token,s,v)):MontogramQuickReplies.clear(before,(s,v)->complete(token,s,v));
            }).create());
    }
}
