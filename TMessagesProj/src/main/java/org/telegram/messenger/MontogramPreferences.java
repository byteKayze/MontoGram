/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/** Finite UI-only schema. Never adds Telegram preferences, credentials or user content. */
public final class MontogramPreferences {
    private MontogramPreferences() {}
    public enum Key {
        MATERIAL3("material3", false, true), MESSAGE_DETAILS("message_details", false, false),
        MOTION("motion", 0, false), READER("reader", false, false),
        AVATAR_SHAPE("avatar_shape", 0, true), HIDE_STORIES("hide_stories", false, true),
        PROFILE_IDS("profile_ids", false, true), DRAFT_COUNTER("draft_counter", false, true), MESSAGE_SECONDS("message_seconds", false, true), CONFIRM_CALLS("confirm_calls", false, false), CONFIRM_STICKERS("confirm_stickers", false, false), DIALOG_DENSITY("dialog_density", 0, true), HAPTICS("haptics", 0, false), MENU_ORDER("menu_order", 0, false),
        MENU_HIDE_TRANSLATE("menu_hide_translate", false, false), MENU_HIDE_SHARE("menu_hide_share", false, false), MENU_HIDE_SAVE("menu_hide_save", false, false), VOICE_PREVIEW("voice_preview", false, false), HIDE_TYPING("hide_typing", false, false);
        public final String externalName;
        public final Object defaultValue;
        public final boolean layoutReload;
        Key(String name, Object value, boolean reload) { externalName=name;defaultValue=value;layoutReload=reload; }
        public boolean isBoolean() { return defaultValue instanceof Boolean; }
        public void validate(Object value) {
            if (isBoolean() ? !(value instanceof Boolean) : !(value instanceof Integer)
                    || ((Integer)value)<0 || ((Integer)value)>(this==MOTION?1:2)) throw new IllegalArgumentException("Invalid UI value");
        }
        public static Key fromName(String name) {
            for(Key key:values())if(key.externalName.equals(name))return key;
            return null;
        }
    }
    public static final class State {
        private static final State DEFAULTS=new State(Collections.emptyMap());
        private final Map<Key,Object> values;
        private State(Map<Key,?> overrides) {
            EnumMap<Key,Object> copy=new EnumMap<>(Key.class);
            for(Key key:Key.values())copy.put(key,key.defaultValue);
            for(Map.Entry<Key,?> e:overrides.entrySet()) {
                if(e.getKey()==null)throw new IllegalArgumentException("Unknown UI key");
                e.getKey().validate(e.getValue());copy.put(e.getKey(),e.getValue());
            }
            values=Collections.unmodifiableMap(copy);
        }
        public static State defaults(){return DEFAULTS;}
        public Object get(Key key){if(key==null)throw new IllegalArgumentException("Missing key");return values.get(key);}
        public boolean bool(Key key){if(!key.isBoolean())throw new IllegalArgumentException("Not a boolean");return(Boolean)get(key);}
        public int integer(Key key){if(key.isBoolean())throw new IllegalArgumentException("Not an integer");return(Integer)get(key);}
        public Map<Key,Object> asMap(){return values;}
        public State with(Key key,Object value){return merge(Collections.singletonMap(key,value));}
        public State merge(Map<Key,?> changes){
            if(changes==null)throw new IllegalArgumentException("Missing changes");
            EnumMap<Key,Object> copy=new EnumMap<>(Key.class);copy.putAll(values);
            for(Map.Entry<Key,?> e:changes.entrySet()){
                if(e.getKey()==null)throw new IllegalArgumentException("Unknown key");
                e.getKey().validate(e.getValue());copy.put(e.getKey(),e.getValue());
            }
            return copy.equals(values)?this:new State(copy);
        }
        @Override public boolean equals(Object o){return o instanceof State&&values.equals(((State)o).values);}
        @Override public int hashCode(){return values.hashCode();}
    }
}
