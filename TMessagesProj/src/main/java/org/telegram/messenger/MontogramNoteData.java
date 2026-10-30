/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/** User-authored whole-chat notes, never a Telegram message body or mirror. */
public final class MontogramNoteData {
    public static final int MAX_COUNT=128,MAX_TEXT=4096,MAX_BYTES=2*1024*1024;
    private static final int MAGIC=0x4d4e5431; // MNT1
    public static final class Entry {
        public final long dialogId;
        public final String text;
        private Entry(long dialogId,String text){
            if(dialogId==0||text==null||text.isEmpty()||text.length()>MAX_TEXT)throw new IllegalArgumentException("Invalid note");
            try{utf8(text);}catch(CharacterCodingException invalid){throw new IllegalArgumentException("Invalid Unicode note",invalid);}
            for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')throw new IllegalArgumentException("Control character in note");}
            this.dialogId=dialogId;this.text=text;
        }
    }
    public final long revision;
    public final List<Entry> entries;
    private MontogramNoteData(long revision,List<Entry> entries){
        if(revision<0||revision<entries.size()||entries.size()>MAX_COUNT)throw new IllegalArgumentException("Invalid note state");
        HashSet<Long> peers=new HashSet<>();for(Entry entry:entries)if(!peers.add(entry.dialogId))throw new IllegalArgumentException("Duplicate note");
        this.revision=revision;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));
    }
    public static MontogramNoteData empty(){return new MontogramNoteData(0,Collections.emptyList());}
    public Entry get(long peer){for(Entry entry:entries)if(entry.dialogId==peer)return entry;return null;}
    private long nextRevision(){if(revision==Long.MAX_VALUE)throw new IllegalArgumentException("Revision exhausted");return revision+1;}
    public MontogramNoteData put(long peer,String text){
        if(peer==0||text==null)throw new IllegalArgumentException("Invalid note");
        Entry previous=get(peer);if(text.isEmpty())return previous==null?this:remove(peer);
        Entry entry=new Entry(peer,text);if(previous!=null&&previous.text.equals(text))return this;
        if(previous==null&&entries.size()==MAX_COUNT)throw new IllegalArgumentException("Note limit reached");
        ArrayList<Entry> copy=new ArrayList<>(entries);if(previous==null)copy.add(entry);else copy.set(entries.indexOf(previous),entry);
        return new MontogramNoteData(nextRevision(),copy);
    }
    public MontogramNoteData remove(long peer){
        if(peer==0)throw new IllegalArgumentException("Invalid note peer");Entry previous=get(peer);if(previous==null)return this;
        ArrayList<Entry> copy=new ArrayList<>(entries);copy.remove(previous);return new MontogramNoteData(nextRevision(),copy);
    }
    public MontogramNoteData removeAll(long[] peers){
        if(peers==null||peers.length>MAX_COUNT)throw new IllegalArgumentException("Invalid selection");
        if(peers.length==0)return this;
        java.util.HashSet<Long> selected=new java.util.HashSet<>();
        for(long peer:peers)if(!selected.add(peer)||get(peer)==null)throw new IllegalArgumentException("Stale or duplicate selected note");
        ArrayList<Entry> copy=new ArrayList<>();for(Entry entry:entries)if(!selected.contains(entry.dialogId))copy.add(entry);
        return new MontogramNoteData(nextRevision(),copy);
    }
    public MontogramNoteData clear(){return entries.isEmpty()?this:new MontogramNoteData(nextRevision(),Collections.emptyList());}
    private static byte[] utf8(String value)throws CharacterCodingException{
        ByteBuffer buffer=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value));
        byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;
    }
    public byte[] encode()throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(MAGIC);out.writeLong(revision);out.writeInt(entries.size());
        for(Entry entry:entries){byte[] text=utf8(entry.text);out.writeLong(entry.dialogId);out.writeInt(text.length);out.write(text);}
        out.flush();if(bytes.size()>MAX_BYTES)throw new IOException("Note payload too large");return bytes.toByteArray();
    }
    public static MontogramNoteData decode(byte[] bytes)throws IOException{
        if(bytes==null||bytes.length>MAX_BYTES)throw new IOException("Invalid note payload size");DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
        try{
            if(input.readInt()!=MAGIC)throw new IOException("Unknown note format");long revision=input.readLong();int count=input.readInt();
            if(count<0||count>MAX_COUNT)throw new IOException("Invalid note count");ArrayList<Entry> entries=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                long peer=input.readLong();int length=input.readInt();if(length<=0||length>MAX_TEXT*3||length>input.available())throw new IOException("Invalid note text length");
                byte[] value=new byte[length];input.readFully(value);String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(value)).toString();entries.add(new Entry(peer,text));
            }
            if(input.available()!=0)throw new IOException("Trailing note payload");return new MontogramNoteData(revision,entries);
        }catch(IllegalArgumentException invalid){throw new IOException("Invalid note payload",invalid);}
    }
}
