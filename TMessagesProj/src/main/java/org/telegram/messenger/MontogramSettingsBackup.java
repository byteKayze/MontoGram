/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.EnumMap;
import java.util.Collections;
import org.telegram.messenger.MontogramPreferences.Key;
import org.telegram.messenger.MontogramPreferences.State;

/** Versioned, bounded UI-only transfer. Never reads Telegram/account preferences. */
public final class MontogramSettingsBackup {
    private static final int MAX_BYTES = 4096;
    private MontogramSettingsBackup() {}

    public static final class Snapshot {
        private final Map<Key,Object> selected;
        public Snapshot(boolean material3,boolean messageDetails) {
            EnumMap<Key,Object> values=new EnumMap<>(Key.class);
            values.put(Key.MATERIAL3,material3);values.put(Key.MESSAGE_DETAILS,messageDetails);
            selected=Collections.unmodifiableMap(values);
        }
        public Snapshot(State state){this(state.asMap());}
        private Snapshot(Map<Key,Object> source){
            if(source.isEmpty())throw new IllegalArgumentException("Empty UI selection");
            EnumMap<Key,Object> copy=new EnumMap<>(Key.class);
            for(Map.Entry<Key,Object> e:source.entrySet()){e.getKey().validate(e.getValue());copy.put(e.getKey(),e.getValue());}
            selected=Collections.unmodifiableMap(copy);
        }
        public State merge(State current){return current.merge(selected);}
        public byte[] encode(){
            StringBuilder json=new StringBuilder("{\"format\":\"montogram-ui\",\"version\":2,\"settings\":{");
            boolean first=true;
            for(Map.Entry<Key,Object> e:selected.entrySet()){
                if(!first)json.append(',');first=false;
                json.append('"').append(e.getKey().externalName).append("\":").append(e.getValue());
            }
            return json.append("}}\n").toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    public static Snapshot read(InputStream input) throws IOException {
        if (input == null) throw new IOException("No document stream");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int count;
        while ((count = input.read(buffer, 0, Math.min(buffer.length, MAX_BYTES + 1 - bytes.size()))) != -1) {
            if (count == 0) throw new IOException("Empty read");
            bytes.write(buffer, 0, count);
            if (bytes.size() > MAX_BYTES) throw new IOException("Document too large");
        }
        final String json;
        try {
            json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
        } catch (CharacterCodingException invalid) { throw new IOException("Invalid UTF-8", invalid); }
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            Set<String> root = new HashSet<>();
            Snapshot result = null;
            int version=0;
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!root.add(key)) throw new IOException("Duplicate field");
                switch (key) {
                    case "format":
                        if (reader.peek() != JsonToken.STRING || !"montogram-ui".equals(reader.nextString()))
                            throw new IOException("Unknown format");
                        break;
                    case "version":
                        if(reader.peek()!=JsonToken.NUMBER)throw new IOException("Unsupported version");
                        String token=reader.nextString();
                        if(!"1".equals(token)&&!"2".equals(token))throw new IOException("Unsupported version");
                        version=Integer.parseInt(token);
                        break;
                    case "settings": result = readSettings(reader); break;
                    default: throw new IOException("Unknown field");
                }
            }
            reader.endObject();
            if (root.size() != 3 || result == null || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Incomplete document");
            if(version==1&&(result.selected.size()!=2||!result.selected.containsKey(Key.MATERIAL3)
                    ||!result.selected.containsKey(Key.MESSAGE_DETAILS)))throw new IOException("Invalid legacy fields");
            return result;
        } catch (IllegalStateException invalid) { throw new IOException("Invalid structure", invalid); }
    }

    private static Snapshot readSettings(JsonReader reader) throws IOException {
        Set<String> fields=new HashSet<>();
        EnumMap<Key,Object> values=new EnumMap<>(Key.class);
        reader.beginObject();
        while(reader.hasNext()){
            String name=reader.nextName();Key key=Key.fromName(name);
            if(!fields.add(name)||key==null)throw new IOException("Invalid UI key");
            Object value;
            if(key.isBoolean()){
                if(reader.peek()!=JsonToken.BOOLEAN)throw new IOException("Invalid boolean");
                value=reader.nextBoolean();
            }else{
                if(reader.peek()!=JsonToken.NUMBER)throw new IOException("Invalid integer");
                String token=reader.nextString();
                try{
                    int parsed=Integer.parseInt(token);
                    if(!token.equals(Integer.toString(parsed)))throw new NumberFormatException();
                    value=parsed;
                }catch(NumberFormatException invalid){throw new IOException("Invalid integer",invalid);}
            }
            try{key.validate(value);}catch(IllegalArgumentException invalid){throw new IOException("Invalid UI value",invalid);}
            values.put(key,value);
        }
        reader.endObject();
        if(values.isEmpty())throw new IOException("Empty UI selection");
        return new Snapshot(values);
    }
}
