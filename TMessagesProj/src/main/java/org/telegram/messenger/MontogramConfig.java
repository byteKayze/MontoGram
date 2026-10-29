/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import java.util.EnumMap;
import org.telegram.messenger.MontogramPreferences.Key;
import org.telegram.messenger.MontogramPreferences.State;

/** Sole UI preferences writer; no account/auth preference access. */
public final class MontogramConfig {
    private MontogramConfig() {}
    public static final Object APPEARANCE_RELOAD=new Object();
    public static final boolean DEFAULT_MATERIAL3=(Boolean)Key.MATERIAL3.defaultValue;
    public static final boolean DEFAULT_MESSAGE_DETAILS=(Boolean)Key.MESSAGE_DETAILS.defaultValue;
    private static volatile State cached;
    private static volatile long revision;
    public static long revision(){return revision;}
    public static State snapshot(){
        State ready=cached;
        if(ready!=null)return ready;
        synchronized(MontogramConfig.class){
        State result=cached;
        if(result==null&&ApplicationLoader.applicationContext!=null){
            SharedPreferences prefs=ApplicationLoader.applicationContext.getSharedPreferences("montogram_appearance",Context.MODE_PRIVATE);
            EnumMap<Key,Object> values=new EnumMap<>(Key.class);
            for(Key key:Key.values()){
                Object value=key.defaultValue;
                try{
                    value=key.isBoolean()?prefs.getBoolean(key.externalName,(Boolean)value):prefs.getInt(key.externalName,(Integer)value);
                    key.validate(value);
                }catch(ClassCastException|IllegalArgumentException invalid){value=key.defaultValue;}
                values.put(key,value);
            }
            cached=result=State.defaults().merge(values);
        }
        return result==null?State.defaults():result;
        }
    }
    public static synchronized boolean apply(State target){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("UI thread required");
        if(target==null)throw new IllegalArgumentException("Missing UI state");
        if(ApplicationLoader.applicationContext==null)return false;
        if(snapshot().equals(target))return true;
        SharedPreferences.Editor editor=ApplicationLoader.applicationContext.getSharedPreferences("montogram_appearance",Context.MODE_PRIVATE).edit();
        for(Key key:Key.values())if(key.isBoolean())editor.putBoolean(key.externalName,target.bool(key));else editor.putInt(key.externalName,target.integer(key));
        if(!editor.commit())return false;
        cached=target;revision++;return true;
    }
    public static boolean isMaterial3(){return snapshot().bool(Key.MATERIAL3);}
    public static boolean showMessageDetails(){return snapshot().bool(Key.MESSAGE_DETAILS);}
    public static int hapticsMode(){return snapshot().integer(Key.HAPTICS);}
    public static int motionMode(){return snapshot().integer(Key.MOTION);}
    public static int dialogDensity(){return snapshot().integer(Key.DIALOG_DENSITY);}
    public static int avatarShape(){return snapshot().integer(Key.AVATAR_SHAPE);}
    public static boolean hideStories(){return snapshot().bool(Key.HIDE_STORIES);}
    public static boolean profileIds(){return snapshot().bool(Key.PROFILE_IDS);}
    public static boolean draftCounter(){return snapshot().bool(Key.DRAFT_COUNTER);}
    public static boolean messageSeconds(){return snapshot().bool(Key.MESSAGE_SECONDS);}
    public static boolean voicePreview(){return snapshot().bool(Key.VOICE_PREVIEW);}
    public static boolean hideTyping(){return snapshot().bool(Key.HIDE_TYPING);}
    public static boolean confirmCalls(){return snapshot().bool(Key.CONFIRM_CALLS);}
    public static boolean confirmStickers(){return snapshot().bool(Key.CONFIRM_STICKERS);}
    public static boolean applyUiSettings(boolean material,boolean details){return apply(snapshot().with(Key.MATERIAL3,material).with(Key.MESSAGE_DETAILS,details));}
    public static boolean setMaterial3(boolean value){return apply(snapshot().with(Key.MATERIAL3,value));}
    public static boolean setMessageDetails(boolean value){return apply(snapshot().with(Key.MESSAGE_DETAILS,value));}
}
