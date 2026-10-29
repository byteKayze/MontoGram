/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;

import android.app.Dialog;
import android.content.Context;
import java.util.function.BooleanSupplier;
import android.text.InputFilter;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MontogramNotes;
import org.telegram.messenger.MontogramNoteData;
import org.telegram.messenger.MontogramNoteSearch;
import org.telegram.messenger.MontogramSettingsIndex;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.MontogramMaterial;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

/** Identifier-only manager for user-authored notes; no chat resolver, network or send API. */
public final class MontogramNotesActivity extends UniversalFragment implements NotificationCenter.NotificationCenterDelegate {
    private static final int ADD=1, RETAIN=2, CLEAR=3, RETRY=4, RESET=5, FIRST=100;
    private MontogramNotes.Snapshot snapshot;
    private MontogramNotes.Request request;
    private MontogramNotes.Status failure;
    private boolean active, busy, destroyed;
    private int generation;
    private NoteEditor editor;
    private EditText searchInput;
    private String query = "";
    private boolean windowSearchVisible = true;
    private ViewTreeObserver searchTree;
    private final ViewTreeObserver.OnWindowFocusChangeListener searchFocus = focused -> {
        windowSearchVisible = focused;
        if (!focused) {
            query = "";
            if (searchInput != null) searchInput.setText("");
            if (listView != null) { eraseView(listView); listView.getRecycledViewPool().clear(); }
            if (SharedConfig.appLocked || SharedConfig.isWaitingForPasscodeEnter || currentAccount != UserConfig.selectedAccount) drop();
            else refresh();
        } else if (!destroyed && !isPaused() && getParentActivity() != null
                && !getParentActivity().isFinishing() && !getParentActivity().isDestroyed()
                && currentAccount == UserConfig.selectedAccount && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter) {
            if (!active) { active = true; load(); } else refresh();
        }
    };
    private long capturePeer;private BooleanSupplier captureAllowed;private boolean transitionReady;

    public MontogramNotesActivity(int account) { setCurrentAccount(account); }
    public MontogramNotesActivity(int account,long peer,BooleanSupplier eligible){this(account);capturePeer=peer;captureAllowed=eligible;}
    @Override public boolean onFragmentCreate() {
        if (!super.onFragmentCreate()) return false;
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.activeAccountChanged);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.appDidLogout);
        return true;
    }
    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.activeAccountChanged || id == NotificationCenter.appDidLogout) drop();
    }
    private void removeSearchFocus() {
        if (searchTree != null && searchTree.isAlive()) searchTree.removeOnWindowFocusChangeListener(searchFocus);
        searchTree = null;
    }
    @Override public View createView(Context context) {
        removeSearchFocus(); query = ""; searchInput = null; windowSearchVisible = true;
        View root = super.createView(context);
        ActionBarMenuItem search = actionBar.createMenu().addItem(90, R.drawable.outline_header_search)
                .setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override public void onSearchCollapse() { query = ""; refresh(); }
                    @Override public void onTextChanged(EditText input) {
                        if (!canSearch()) {
                            query = "";
                            if (input.length() != 0) input.setText("");
                        } else query = MontogramNoteSearch.query(input.getText().toString());
                        refresh();
                    }
                });
        search.setSearchFieldHint(text(R.string.Search));
        searchInput = search.getSearchField(); searchInput.setSaveEnabled(false);
        searchInput.setEnabled(canSearch());
        searchInput.setImeOptions(searchInput.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchInput.setFilters(new InputFilter[]{MontogramSettingsIndex::filterInsertion});
        searchTree = root.getViewTreeObserver(); searchTree.addOnWindowFocusChangeListener(searchFocus);
        return root;
    }
    private static String text(int id) { return LocaleController.getString(id); }
    @Override public Theme.ResourcesProvider getResourceProvider() { return MontogramMaterial.scoped(super.getResourceProvider()); }
    @Override protected CharSequence getTitle() { return text(R.string.MontogramNotes); }
    private boolean alive(int token) { return active&&!destroyed&&token==generation&&getParentActivity()!=null&&!getParentActivity().isFinishing()&&!getParentActivity().isDestroyed()&&currentAccount==UserConfig.selectedAccount; }
    private boolean canSearch() { return windowSearchVisible && !busy && alive(generation) && !SharedConfig.appLocked && !SharedConfig.isWaitingForPasscodeEnter && snapshot != null && MontogramNotes.current(snapshot); }
    private void refresh() { if(searchInput!=null)searchInput.setEnabled(canSearch());if(listView!=null)listView.adapter.update(false); }
    private void load() {
        if(!alive(generation)||busy)return;
        busy=true;failure=null;refresh();final int token=generation;
        request=MontogramNotes.load(currentAccount,(status,value)->complete(token,status,value));
    }
    private void complete(int token,MontogramNotes.Status status,MontogramNotes.Snapshot value) {
        if(!alive(token)){MontogramNotes.revoke(value);return;}
        busy=false;request=null;
        if(status==MontogramNotes.Status.OK){snapshot=value;failure=null;}
        else if(status!=MontogramNotes.Status.INVALID&&status!=MontogramNotes.Status.BUSY){snapshot=null;failure=status;}
        refresh();maybeEditCaptured();
        if(status==MontogramNotes.Status.INVALID)message(R.string.MontogramNotesInvalid);
        else if(status!=MontogramNotes.Status.OK&&status!=MontogramNotes.Status.BUSY)message(R.string.MontogramNotesError);
    }
    private void message(int id) { if(alive(generation))showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(getTitle()).setMessage(text(id)).setPositiveButton(text(R.string.OK),null).create()); }
    @Override public void onResume(){super.onResume();active=true;load();}
    private static void eraseView(View view){if(view instanceof TextView)((TextView)view).setText("");if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)eraseView(group.getChildAt(i));}}
    private void drop() {
        active=false;++generation;captureAllowed=null;capturePeer=0;transitionReady=false;
        query = ""; if (searchInput != null) searchInput.setText("");
        if(request!=null)request.cancel();request=null;busy=false;
        MontogramNotes.revoke(snapshot);snapshot=null;failure=null;
        if(editor!=null)editor.closeAndErase();
        Dialog dialog=getVisibleDialog();if(dialog!=null){eraseView(dialog.getWindow()==null?null:dialog.getWindow().getDecorView());AndroidUtilities.runOnUIThread(dialog::dismiss);}
        if(listView!=null){eraseView(listView);refresh();listView.getRecycledViewPool().clear();}
    }
    @Override public void onPause(){drop();super.onPause();}
    @Override public void onFragmentDestroy(){destroyed=true;removeSearchFocus();NotificationCenter.getGlobalInstance().removeObserver(this,NotificationCenter.activeAccountChanged);NotificationCenter.getInstance(currentAccount).removeObserver(this,NotificationCenter.appDidLogout);drop();searchInput=null;super.onFragmentDestroy();}
    @Override protected void fillItems(ArrayList<UItem> items,UniversalAdapter adapter) {
        if(!active||busy||!windowSearchVisible){items.add(UItem.asShadow(text(R.string.Loading)));return;}
        List<MontogramNoteData.Entry> rows=MontogramNotes.entries(snapshot);
        if(rows==null){items.add(UItem.asShadow(text(R.string.MontogramNotesError)));items.add(UItem.asButton(RETRY,R.drawable.settings_chat,text(R.string.Retry)));if(failure!=null)items.add(UItem.asButton(RESET,R.drawable.settings_chat,text(R.string.MontogramNotesReset)));return;}
        items.add(UItem.asShadow(text(MontogramNotes.mode(snapshot)==MontogramNotes.Mode.RETAINED?R.string.MontogramNotesRetainedInfo:R.string.MontogramNotesMemoryInfo)));
        int matches = 0;
        for(int i=0;i<rows.size();i++)if(MontogramNoteSearch.matches(rows.get(i).text,query)){
            items.add(UItem.asButton(FIRST+i,R.drawable.settings_chat,Long.toString(rows.get(i).dialogId)));matches++;
        }
        if(!rows.isEmpty()&&matches==0)items.add(UItem.asShadow(text(R.string.NoResult)));
        if(rows.isEmpty())items.add(UItem.asShadow(text(R.string.MontogramNotesEmpty)));
        if(MontogramNotes.mode(snapshot)==MontogramNotes.Mode.PROCESS_MEMORY)items.add(UItem.asButton(RETAIN,R.drawable.settings_chat,text(R.string.MontogramNotesRetain)));
        items.add(UItem.asButton(CLEAR,R.drawable.settings_chat,text(R.string.MontogramNotesReset)));
    }
    @Override protected boolean onLongClick(UItem item,View view,int position,float x,float y){return false;}
    @Override protected void onClick(UItem item,View view,int position,float x,float y) {
        if(!alive(generation)||busy)return;
        if(item.id==RETRY){load();return;}
        if(item.id==RESET){confirmClear(true);return;}
        if(!MontogramNotes.current(snapshot)){load();return;}
        if(item.id==RETAIN)confirmRetention();
        else if(item.id==CLEAR)confirmClear(false);
        else {
            List<MontogramNoteData.Entry> rows=MontogramNotes.entries(snapshot);int index=item.id-FIRST;
            if(rows!=null&&index>=0&&index<rows.size())edit(rows.get(index).dialogId,null);
        }
    }
    @Override public void onTransitionAnimationEnd(boolean open,boolean backward){super.onTransitionAnimationEnd(open,backward);if(open){transitionReady=true;maybeEditCaptured();}}
    private void maybeEditCaptured(){
        if(!transitionReady||busy||!alive(generation)||captureAllowed==null||!MontogramNotes.current(snapshot))return;
        BooleanSupplier grant=captureAllowed;long peer=capturePeer;captureAllowed=null;capturePeer=0;
        if(!grant.getAsBoolean())return;edit(peer,grant);
    }
    private void edit(long peer,BooleanSupplier context){
        if(!alive(generation)||busy)return;
        List<MontogramNoteData.Entry> rows=MontogramNotes.entries(snapshot);if(rows==null)return;MontogramNoteData.Entry existing=null;
        for(MontogramNoteData.Entry row:rows)if(row.dialogId==peer)existing=row;
        if(editor!=null)editor.closeAndErase();
        editor=new NoteEditor(peer,context,snapshot);
        editor.open(existing);
    }
    /** Own-input session only; failure never silently clears the editor or retries a disk write. */
    private final class NoteEditor {
        private final long peer,self=UserConfig.getInstance(currentAccount).getClientUserId();
        private final int token=generation;
        private BooleanSupplier context;
        private MontogramNotes.Snapshot before;
        private MontogramNotes.Request pending;
        private EditText body;
        private AlertDialog dialog;
        private View saveButton,deleteButton;
        private ViewTreeObserver tree;
        private ViewTreeObserver.OnWindowFocusChangeListener focus;
        private boolean closed,saving,reloadRequired;
        private int operation;
        NoteEditor(long peer,BooleanSupplier context,MontogramNotes.Snapshot before){this.peer=peer;this.context=context;this.before=before;}
        private boolean own(){return !closed&&editor==this&&alive(token)&&!SharedConfig.appLocked&&!SharedConfig.isWaitingForPasscodeEnter&&self>0&&self==UserConfig.getInstance(currentAccount).getClientUserId();}
        private boolean writable(){return own()&&!saving&&!reloadRequired&&MontogramNotes.current(before);}
        private void controls(){
            if(body!=null)body.setEnabled(!saving);
            boolean allowed=writable();
            if(saveButton!=null)saveButton.setEnabled(allowed&&(context==null||context.getAsBoolean()));
            if(deleteButton!=null)deleteButton.setEnabled(allowed);
        }
        void open(MontogramNoteData.Entry existing){
            if(!own()){closeAndErase();return;}
            body=new EditText(getParentActivity());body.setSaveEnabled(false);
            body.setImeOptions(body.getImeOptions()|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            body.setHint(text(R.string.MontogramNotesText));body.setMinLines(4);body.setMaxLines(12);body.setTextColor(getThemedColor(Theme.key_dialogTextBlack));body.setHintTextColor(getThemedColor(Theme.key_dialogTextHint));
            body.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramNoteData.MAX_TEXT)});if(existing!=null)body.setText(existing.text);
            LinearLayout fields=new LinearLayout(getParentActivity());fields.setSaveEnabled(false);fields.setPadding(AndroidUtilities.dp(24),0,AndroidUtilities.dp(24),0);fields.addView(body,new LinearLayout.LayoutParams(-1,-2));
            AlertDialog.Builder builder=new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(getTitle()+" · "+peer).setView(fields)
                    .setNegativeButton(text(R.string.Cancel),(d,w)->closeAndErase()).setPositiveButton(text(R.string.Save),null);
            if(existing!=null)builder.setNeutralButton(text(R.string.Delete),null);
            dialog=builder.create();final AlertDialog observed=dialog;
            if(showDialog(observed,d->closeAndErase())==null){closeAndErase();return;}
            saveButton=observed.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            deleteButton=observed.getButton(android.content.DialogInterface.BUTTON_NEUTRAL);
            if(saveButton==null){closeAndErase();return;}
            saveButton.setOnClickListener(v->begin(false));
            if(deleteButton!=null)deleteButton.setOnClickListener(v->begin(true));
            if(observed.getWindow()!=null){
                final View decor=observed.getWindow().getDecorView();
                tree=decor.getViewTreeObserver();
                focus=new ViewTreeObserver.OnWindowFocusChangeListener(){
                    private boolean focused=decor.hasWindowFocus();
                    @Override public void onWindowFocusChanged(boolean hasFocus){
                        if(dialog!=observed||closed)return;
                        if(!own()){closeAndErase();return;}
                        if(hasFocus)focused=true;else if(focused)closeAndErase();
                    }
                };tree.addOnWindowFocusChangeListener(focus);
                AndroidUtilities.runOnUIThread(()->{
                    if(dialog!=observed||closed)return;
                    if(!own())closeAndErase();
                    else if(decor.hasWindowFocus()&&focus!=null)focus.onWindowFocusChanged(true);
                });
            }
            controls();
        }
        private void begin(boolean deleting){
            if(!own()){closeAndErase();return;}
            if(saving)return;
            if(!writable()||(!deleting&&context!=null&&!context.getAsBoolean())){
                body.setError(text(R.string.MontogramNotesError));controls();return;
            }
            saving=true;busy=true;final int ticket=++operation;controls();refresh();
            MontogramNotes.Callback callback=(status,result)->finished(ticket,status,result);
            MontogramNotes.Request issued=deleting?MontogramNotes.delete(before,peer,callback)
                    :MontogramNotes.save(before,peer,body.getText().toString(),callback);
            // INVALID/BUSY/process-memory OK may invoke the callback synchronously.
            if(!closed&&saving&&operation==ticket)pending=issued;
            else if(issued!=null)issued.cancel();
        }
        private void finished(int ticket,MontogramNotes.Status status,MontogramNotes.Snapshot result){
            if(!own()||!saving||operation!=ticket){
                if(result!=snapshot)MontogramNotes.revoke(result);
                if(!own())closeAndErase();return;
            }
            saving=false;pending=null;busy=false;
            if(status==MontogramNotes.Status.OK&&result!=null&&MontogramNotes.current(result)){
                snapshot=result;failure=null;closeAndErase();refresh();return;
            }
            if(result!=snapshot)MontogramNotes.revoke(result);
            if(status!=MontogramNotes.Status.INVALID&&status!=MontogramNotes.Status.BUSY){
                reloadRequired=true;MontogramNotes.revoke(before);snapshot=null;failure=status==MontogramNotes.Status.OK?MontogramNotes.Status.STALE:status;
            }
            body.setError(text(status==MontogramNotes.Status.INVALID?R.string.MontogramNotesInvalid:R.string.MontogramNotesError));
            controls();refresh();
        }
        void closeAndErase(){
            if(closed)return;closed=true;++operation;
            if(tree!=null&&tree.isAlive()&&focus!=null)tree.removeOnWindowFocusChangeListener(focus);
            tree=null;focus=null;
            if(pending!=null)pending.cancel();pending=null;
            if(body!=null)body.setText("");body=null;saveButton=null;deleteButton=null;before=null;context=null;
            AlertDialog previous=dialog;dialog=null;
            if(editor==this){editor=null;busy=false;refresh();}
            if(previous!=null)AndroidUtilities.runOnUIThread(previous::dismiss);
        }
    }
    private void confirmRetention() {
        final int token=generation;final MontogramNotes.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramNotesRetain))
            .setMessage(text(R.string.MontogramNotesRetainConfirm)).setNegativeButton(text(R.string.Cancel),null)
            .setPositiveButton(text(R.string.MontogramNotesRetain),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||!MontogramNotes.current(before))return;
                busy=true;refresh();request=MontogramNotes.retainAfterConfirmedUserAction(before,(s,v)->complete(token,s,v));
            }).create());
    }
    private void confirmClear(boolean recover) {
        final int token=generation;final MontogramNotes.Snapshot before=snapshot;final AtomicBoolean used=new AtomicBoolean();
        showDialog(new AlertDialog.Builder(getParentActivity(),getResourceProvider()).setTitle(text(R.string.MontogramNotesReset))
            .setMessage(text(R.string.MontogramNotesResetConfirm)).setNegativeButton(text(R.string.Cancel),null)
            .setPositiveButton(text(R.string.Delete),(d,w)->{
                if(!used.compareAndSet(false,true)||!alive(token)||(!recover&&!MontogramNotes.current(before)))return;
                busy=true;refresh();request=recover?MontogramNotes.resetRetained(currentAccount,(s,v)->complete(token,s,v)):MontogramNotes.clear(before,(s,v)->complete(token,s,v));
            }).create());
    }
}
