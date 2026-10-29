/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui.Components;

import android.app.Dialog;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.MontogramQuickReplies;
import org.telegram.messenger.MontogramQuickReplyData;
import org.telegram.messenger.MontogramQuickReplySearch;
import org.telegram.messenger.MontogramSettingsIndex;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

/** One ordinary-chat draft insertion; no send API and no clipboard. */
public final class MontogramQuickReplyPicker implements NotificationCenter.NotificationCenterDelegate {
    private final int account;
    private boolean observing;
    private EditText queryInput;
    private LinearLayout resultList;
    private ViewTreeObserver dialogTree;
    private ViewTreeObserver.OnWindowFocusChangeListener dialogFocus;
    private BaseFragment fragment;
    private BooleanSupplier eligible;
    private Supplier<CharSequence> draft;
    private Consumer<String> insert;
    private MontogramQuickReplies.Snapshot snapshot;
    private MontogramQuickReplies.Request request;
    private Dialog dialog;
    private CharSequence[] labels;
    private List<MontogramQuickReplyData.Reply> rows;
    private String pending, expectedDraft;
    private boolean closed, selected;
    public MontogramQuickReplyPicker(BaseFragment fragment,int account,BooleanSupplier eligible,Supplier<CharSequence> draft,Consumer<String> insert) {
        this.account=account;
        this.fragment=fragment;this.eligible=eligible;this.draft=draft;this.insert=insert;
        if(!alive()){close();return;}
        NotificationCenter.getGlobalInstance().addObserver(this,NotificationCenter.activeAccountChanged);
        NotificationCenter.getInstance(account).addObserver(this,NotificationCenter.appDidLogout);
        observing=true;
        request=MontogramQuickReplies.load(account,(status,value)->{
            request=null;
            if(!alive()){MontogramQuickReplies.revoke(value);close();return;}
            if(status!=MontogramQuickReplies.Status.OK){message(R.string.MontogramQuickRepliesError);return;}
            snapshot=value;rows=MontogramQuickReplies.entries(value);
            if(rows==null){close();return;}
            if(rows.isEmpty()){message(R.string.MontogramQuickRepliesEmpty);return;}
            labels=new CharSequence[rows.size()];for(int i=0;i<rows.size();i++)labels[i]=rows.get(i).label;
            showSearch();
        });
    }
    private static String text(int id){return LocaleController.getString(id);}
    private boolean alive(){return !closed&&fragment!=null&&fragment.getParentActivity()!=null&&!fragment.getParentActivity().isFinishing()&&!fragment.getParentActivity().isDestroyed()&&!fragment.isPaused()&&eligible!=null&&eligible.getAsBoolean();}
    private boolean valid(){return alive()&&MontogramQuickReplies.current(snapshot);}
    @Override public void didReceivedNotification(int id,int account,Object... args){if(id==NotificationCenter.activeAccountChanged||id==NotificationCenter.appDidLogout)close();}
    private void detachFocus(){if(dialogTree!=null&&dialogTree.isAlive()&&dialogFocus!=null)dialogTree.removeOnWindowFocusChangeListener(dialogFocus);dialogTree=null;dialogFocus=null;}
    private void show(AlertDialog next){
        detachFocus();dialog=next;
        if(fragment.showDialog(next,d->{if(dialog==next){dialog=null;close();}})==null){close();return;}
        if(closed||dialog!=next||next.getWindow()==null)return;
        dialogTree=next.getWindow().getDecorView().getViewTreeObserver();
        dialogFocus=new ViewTreeObserver.OnWindowFocusChangeListener(){
            private boolean focused;
            @Override public void onWindowFocusChanged(boolean hasFocus){
                if(dialog!=next||closed)return;
                if(!alive()){close();return;}
                if(hasFocus)focused=true;else if(focused)close();
            }
        };
        dialogTree.addOnWindowFocusChangeListener(dialogFocus);
    }
    private void showSearch(){
        if(!valid()){close();return;}
        LinearLayout content=new LinearLayout(fragment.getParentActivity());content.setOrientation(LinearLayout.VERTICAL);content.setSaveEnabled(false);
        queryInput=new EditText(fragment.getParentActivity());queryInput.setSaveEnabled(false);
        queryInput.setSingleLine(true);queryInput.setHint(text(R.string.Search));
        queryInput.setTextColor(Theme.getColor(Theme.key_dialogTextBlack,fragment.getResourceProvider()));
        queryInput.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint,fragment.getResourceProvider()));
        queryInput.setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(8),AndroidUtilities.dp(24),AndroidUtilities.dp(8));
        queryInput.setImeOptions(queryInput.getImeOptions()|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        queryInput.setFilters(new InputFilter[]{MontogramSettingsIndex::filterInsertion});
        content.addView(queryInput,LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,LayoutHelper.WRAP_CONTENT));
        resultList=new LinearLayout(fragment.getParentActivity());resultList.setOrientation(LinearLayout.VERTICAL);resultList.setSaveEnabled(false);
        content.addView(resultList,LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,LayoutHelper.WRAP_CONTENT));
        queryInput.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render();}public void afterTextChanged(Editable e){}});
        render();
        if(closed)return;
        show(new AlertDialog.Builder(fragment.getParentActivity(),fragment.getResourceProvider()).setTitle(text(R.string.MontogramQuickReplies))
                .setView(content).setNegativeButton(text(R.string.Cancel),(d,w)->close()).create());
    }
    private void render(){
        if(closed||resultList==null||queryInput==null)return;
        if(!valid()){close();return;}
        erase(resultList);resultList.removeAllViews();
        int[] indices=MontogramQuickReplySearch.order(rows,queryInput.getText().toString());
        for(int originalIndex:indices){
            AlertDialog.AlertDialogCell cell=new AlertDialog.AlertDialogCell(fragment.getParentActivity(),fragment.getResourceProvider());
            cell.setTextAndIcon(labels[originalIndex],0);
            cell.setOnClickListener(v->select(originalIndex));
            resultList.addView(cell,LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,48));
        }
        if(indices.length==0){TextView empty=new TextView(fragment.getParentActivity());empty.setText(text(R.string.NoResult));empty.setTextColor(Theme.getColor(Theme.key_dialogTextBlack,fragment.getResourceProvider()));empty.setPadding(AndroidUtilities.dp(24),AndroidUtilities.dp(16),AndroidUtilities.dp(24),AndroidUtilities.dp(16));resultList.addView(empty);}
    }
    private void clearSearch(){
        // Detach references first: setText invokes our watcher synchronously.
        EditText input=queryInput;LinearLayout results=resultList;queryInput=null;resultList=null;
        if(input!=null)input.setText("");
        if(results!=null){erase(results);results.removeAllViews();}
    }
    private void message(int id){show(new AlertDialog.Builder(fragment.getParentActivity(),fragment.getResourceProvider()).setTitle(text(R.string.MontogramQuickReplies)).setMessage(text(id)).setPositiveButton(text(R.string.OK),(d,w)->close()).create());}
    private void select(int index){
        if(selected||!valid()||rows==null||index<0||index>=rows.size()){close();return;}
        selected=true;pending=rows.get(index).text;expectedDraft=String.valueOf(draft.get()==null?"":draft.get());
        // Detach the old list's dismiss handler before opening a second dialog.
        if(dialog!=null){Dialog old=dialog;dialog=null;detachFocus();clearSearch();old.dismiss();}
        if(expectedDraft.isEmpty()){commit();return;}
        show(new AlertDialog.Builder(fragment.getParentActivity(),fragment.getResourceProvider()).setTitle(text(R.string.MontogramQuickReplies))
            .setMessage(text(R.string.MontogramQuickRepliesReplace)).setNegativeButton(text(R.string.Cancel),(d,w)->close())
            .setPositiveButton(text(R.string.OK),(d,w)->commit()).create());
    }
    private void commit(){
        if(!selected||pending==null||!valid()||!expectedDraft.contentEquals(draft.get()==null?"":draft.get())){close();return;}
        String value=pending;Consumer<String> target=insert;BooleanSupplier guard=eligible;
        pending=null;selected=false;
        // One-shot state is consumed before calling native editor code.
        close();
        if(guard.getAsBoolean())target.accept(value);
    }
    private static void erase(View view){if(view instanceof TextView)((TextView)view).setText("");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)erase(group.getChildAt(i));}}
    public void close(){
        if(closed)return;closed=true;
        detachFocus();
        if(observing){observing=false;NotificationCenter.getGlobalInstance().removeObserver(this,NotificationCenter.activeAccountChanged);NotificationCenter.getInstance(account).removeObserver(this,NotificationCenter.appDidLogout);}
        clearSearch();
        if(request!=null)request.cancel();request=null;
        MontogramQuickReplies.revoke(snapshot);snapshot=null;
        if(labels!=null)Arrays.fill(labels,null);labels=null;rows=null;pending=null;expectedDraft=null;
        if(dialog!=null){Dialog old=dialog;dialog=null;if(old.getWindow()!=null)erase(old.getWindow().getDecorView());AndroidUtilities.runOnUIThread(old::dismiss);}
        fragment=null;eligible=null;draft=null;insert=null;
    }
}
