/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MontogramReaderSearch;
import org.telegram.messenger.MontogramTemporaryClipboard;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.MontogramMaterial;
import org.telegram.ui.ActionBar.Theme;
/** Transient selected-message plain-text reader. Never saves body or query in args/state/disk. */
public final class MontogramReaderActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private static final int[] EVENTS={NotificationCenter.messagesDeleted,NotificationCenter.replaceMessagesObjects,NotificationCenter.chatInfoDidLoad,NotificationCenter.userInfoDidLoad,NotificationCenter.appDidLogout};
    private final long self;private final Object clipScope=new Object();
    private String body;private BooleanSupplier permitted;private boolean active,expired,destroyed,focused;
    private LinearLayout root;private ScrollView scroll;private TextView output,counter;private EditText query;private Button previous,next,copy;
    private MontogramReaderSearch.Hits hits;private int index=-1;
    private ViewTreeObserver.OnPreDrawListener drawGuard;private ViewTreeObserver.OnWindowFocusChangeListener focusGuard;
    public MontogramReaderActivity(int account,long self,String body,BooleanSupplier permitted){
        setCurrentAccount(account);MontogramReaderSearch.validate(body,MontogramReaderSearch.MAX_TEXT);this.self=self;this.body=body;this.permitted=permitted;
    }
    private static String text(int id){return LocaleController.getString(id);}
    private boolean authorized(){
        try{return !expired&&!destroyed&&permitted!=null&&currentAccount==UserConfig.selectedAccount&&self>0&&self==UserConfig.getInstance(currentAccount).getClientUserId()&&UserConfig.getInstance(currentAccount).isClientActivated()&&!SharedConfig.appLocked&&!SharedConfig.isWaitingForPasscodeEnter&&getParentActivity()!=null&&!getParentActivity().isFinishing()&&!getParentActivity().isDestroyed()&&permitted.getAsBoolean();}catch(Exception gone){return false;}
    }
    private boolean alive(){return active&&body!=null&&authorized();}
    @Override public boolean onFragmentCreate(){if(!super.onFragmentCreate())return false;for(int event:EVENTS)NotificationCenter.getInstance(currentAccount).addObserver(this,event);NotificationCenter.getGlobalInstance().addObserver(this,NotificationCenter.activeAccountChanged);return true;}
    @Override public Theme.ResourcesProvider getResourceProvider(){return MontogramMaterial.scoped(super.getResourceProvider());}
    @Override public View createView(Context context){
        actionBar.setTitle(text(R.string.MontogramReader));actionBar.setBackButtonImage(R.drawable.ic_ab_back);actionBar.applyMontogramMaterial3();
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){@Override public void onItemClick(int id){if(id==-1)finishFragment();}});
        root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setLayoutDirection(LocaleController.isRTL?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);root.setSaveEnabled(false);root.setSaveFromParentEnabled(false);root.setVisibility(View.INVISIBLE);root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));fragmentView=root;
        query=new EditText(context);query.setSaveEnabled(false);query.setSingleLine(true);query.setHint(text(R.string.Search));query.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));query.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteHintText));query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MontogramReaderSearch.MAX_QUERY)});query.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);query.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);root.addView(query,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout navigation=new LinearLayout(context);previous=button(context,R.string.MontogramReaderPrevious);next=button(context,R.string.MontogramReaderNext);counter=new TextView(context);counter.setSaveEnabled(false);counter.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));counter.setGravity(android.view.Gravity.CENTER);navigation.addView(previous,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(counter,new LinearLayout.LayoutParams(0,-2,1));navigation.addView(next,new LinearLayout.LayoutParams(0,-2,1));root.addView(navigation);
        scroll=new ScrollView(context);scroll.setSaveEnabled(false);output=new TextView(context){@Override protected void onMeasure(int width,int height){int max=AndroidUtilities.dp(720);if(View.MeasureSpec.getSize(width)>max)width=View.MeasureSpec.makeMeasureSpec(max,View.MeasureSpec.EXACTLY);super.onMeasure(width,height);}};output.setSaveEnabled(false);output.setTextSize(18);output.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));output.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(16),AndroidUtilities.dp(20),AndroidUtilities.dp(24));output.setLineSpacing(AndroidUtilities.dp(3),1);output.setTextIsSelectable(false);scroll.addView(output,new android.widget.FrameLayout.LayoutParams(-1,-2,android.view.Gravity.CENTER_HORIZONTAL));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView info=new TextView(context);info.setText(text(R.string.MontogramTemporaryCopyInfo));info.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));info.setPadding(AndroidUtilities.dp(16),0,AndroidUtilities.dp(16),0);root.addView(info);
        copy=button(context,R.string.MontogramTemporaryCopy);root.addView(copy);copy.setOnClickListener(view->{if(alive()){boolean done=MontogramTemporaryClipboard.copy(clipScope,getParentActivity(),currentAccount,self,body);counter.setText(text(done?R.string.TextCopied:R.string.MontogramTemporaryCopyFailed));}else drop();});
        previous.setOnClickListener(view->move(-1));next.setOnClickListener(view->move(1));query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(alive())search();}public void afterTextChanged(Editable value){}});
        drawGuard=()->{if(active&&!authorized())drop();return true;};focusGuard=value->{if(value)focused=true;else if(focused)drop();};root.getViewTreeObserver().addOnPreDrawListener(drawGuard);root.getViewTreeObserver().addOnWindowFocusChangeListener(focusGuard);return root;
    }
    private Button button(Context context,int title){Button button=new Button(context);button.setText(text(title));button.setSaveEnabled(false);return button;}
    @Override public void onResume(){super.onResume();if(!authorized()){drop();return;}active=true;if(root!=null){root.setVisibility(View.VISIBLE);focused=root.hasWindowFocus();search();}}
    private void search(){
        if(!alive()||output==null)return;
        try{hits=MontogramReaderSearch.find(body,query.getText().toString());index=hits.offsets.length==0?-1:0;render();}catch(IllegalArgumentException invalid){hits=null;index=-1;output.setText(body);counter.setText(text(R.string.MontogramReaderNoMatches));previous.setEnabled(false);next.setEnabled(false);}
    }
    private void move(int direction){if(!alive()||hits==null||hits.offsets.length==0)return;index=(index+direction+hits.offsets.length)%hits.offsets.length;render();}
    private void render(){
        if(!alive()||output==null)return;SpannableString display=new SpannableString(body);
        if(hits!=null&&index>=0)display.setSpan(new BackgroundColorSpan((getThemedColor(Theme.key_windowBackgroundWhiteBlueText)&0x00ffffff)|0x44000000),hits.offsets[index],hits.offsets[index]+hits.width,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        output.setText(display);boolean found=hits!=null&&index>=0;previous.setEnabled(found);next.setEnabled(found);counter.setText(found?String.format(Locale.getDefault(),text(R.string.MontogramReaderMatches),index+1,hits.offsets.length)+(hits.more?"+":""):text(R.string.MontogramReaderNoMatches));
        if(found){final int target=hits.offsets[index];output.post(()->{if(alive()&&output.getLayout()!=null&&hits!=null&&index>=0&&hits.offsets[index]==target)scroll.smoothScrollTo(0,output.getLayout().getLineTop(output.getLayout().getLineForOffset(target)));});}
    }
    private void drop(){
        MontogramTemporaryClipboard.revoke(clipScope);active=false;expired=true;body=null;permitted=null;hits=null;index=-1;
        if(query!=null){query.setText("");query.setVisibility(View.GONE);}if(output!=null)output.setText(text(R.string.MontogramReaderClosed));if(counter!=null)counter.setText("");if(copy!=null)copy.setEnabled(false);if(previous!=null)previous.setEnabled(false);if(next!=null)next.setEnabled(false);if(root!=null)root.setVisibility(View.VISIBLE);
    }
    @Override public void onPause(){drop();super.onPause();}
    @Override public void didReceivedNotification(int id,int account,Object... args){drop(); // Conservative account-event revocation; never wait for the parent cache to update.}
    @Override public void onFragmentDestroy(){
        drop();destroyed=true;for(int event:EVENTS)NotificationCenter.getInstance(currentAccount).removeObserver(this,event);NotificationCenter.getGlobalInstance().removeObserver(this,NotificationCenter.activeAccountChanged);
        if(root!=null&&root.getViewTreeObserver().isAlive()){if(drawGuard!=null)root.getViewTreeObserver().removeOnPreDrawListener(drawGuard);if(focusGuard!=null)root.getViewTreeObserver().removeOnWindowFocusChangeListener(focusGuard);}drawGuard=null;focusGuard=null;super.onFragmentDestroy();
    }
}
