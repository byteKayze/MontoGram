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

/** Bounded immutable user-authored templates; no persistence, account access or send API. */
public final class MontogramQuickReplyData {
    public static final int MAX_COUNT=32, MAX_LABEL=64, MAX_TEXT=2048, MAX_BYTES=262144;
    private static final int MAGIC=0x4d515231;
    public static final class Reply {
        public final long id;
        public final String label,text;
        private Reply(long id,String label,String text) {
            if(id<=0 || label==null || text==null)throw new IllegalArgumentException("Invalid template");
            label=label.trim();
            validateText(label,MAX_LABEL);
            validateText(text,MAX_TEXT);
            for(int i=0;i<text.length();i++) {
                char c=text.charAt(i);
                if(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')
                    throw new IllegalArgumentException("Control character in text");
            }
            if(!meaningful(label) || !meaningful(text))throw new IllegalArgumentException("Empty template");
            for(int i=0;i<label.length();i++)if(Character.isISOControl(label.charAt(i)))
                throw new IllegalArgumentException("Control character in label");
            this.id=id;this.label=label;this.text=text;
        }
    }
    public final long revision;
    private final long nextId;
    public final List<Reply> replies;
    private MontogramQuickReplyData(long revision,long nextId,List<Reply> replies) {
        if(revision<0 || nextId<=0 || replies.size()>MAX_COUNT)throw new IllegalArgumentException("Invalid template state");
        if(revision==0 && (nextId!=1 || !replies.isEmpty()))
            throw new IllegalArgumentException("Invalid initial template state");
        HashSet<Long> ids=new HashSet<>();
        for(Reply reply:replies)if(reply.id>=nextId || !ids.add(reply.id))
            throw new IllegalArgumentException("Invalid template identity");
        this.revision=revision;this.nextId=nextId;
        this.replies=Collections.unmodifiableList(new ArrayList<>(replies));
    }
    public static MontogramQuickReplyData empty() {
        return new MontogramQuickReplyData(0,1,Collections.emptyList());
    }
    private long nextRevision() {
        if(revision==Long.MAX_VALUE)throw new IllegalArgumentException("Revision exhausted");
        return revision+1;
    }
    public MontogramQuickReplyData add(String label,String text) {
        if(replies.size()==MAX_COUNT || nextId==Long.MAX_VALUE)
            throw new IllegalArgumentException("Template limit reached");
        ArrayList<Reply> copy=new ArrayList<>(replies);
        copy.add(new Reply(nextId,label,text));
        return new MontogramQuickReplyData(nextRevision(),nextId+1,copy);
    }
    public MontogramQuickReplyData edit(long id,String label,String text) {
        Reply replacement=new Reply(id,label,text);
        ArrayList<Reply> copy=new ArrayList<>(replies);
        for(int i=0;i<copy.size();i++)if(copy.get(i).id==id) {
            Reply previous=copy.get(i);
            if(previous.label.equals(replacement.label)&&previous.text.equals(replacement.text))return this;
            copy.set(i,replacement);
            return new MontogramQuickReplyData(nextRevision(),nextId,copy);
        }
        throw new IllegalArgumentException("Unknown template");
    }
    public MontogramQuickReplyData delete(long id) {
        ArrayList<Reply> copy=new ArrayList<>(replies);
        for(int i=0;i<copy.size();i++)if(copy.get(i).id==id) {
            copy.remove(i);
            return new MontogramQuickReplyData(nextRevision(),nextId,copy);
        }
        throw new IllegalArgumentException("Unknown template");
    }
    private static boolean meaningful(String text) {
        for(int i=0;i<text.length();) {
            int c=text.codePointAt(i);i+=Character.charCount(c);
            if(!Character.isWhitespace(c)&&!Character.isSpaceChar(c)&&Character.getType(c)!=Character.FORMAT)return true;
        }
        return false;
    }
    private static void validateText(String text,int limit) {
        if(text.length()>limit || text.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid template text");
        try { utf8(text); } catch(CharacterCodingException invalid) {
            throw new IllegalArgumentException("Invalid Unicode",invalid);
        }
    }
    private static byte[] utf8(String text) throws CharacterCodingException {
        ByteBuffer encoded=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(text));
        byte[] bytes=new byte[encoded.remaining()];encoded.get(bytes);return bytes;
    }
    private static void writeText(DataOutputStream output,String value) throws IOException {
        byte[] bytes=utf8(value);output.writeInt(bytes.length);output.write(bytes);
    }
    private static String readText(DataInputStream input,int limit) throws IOException {
        int length=input.readInt();
        if(length<0 || length>limit*4 || length>input.available())throw new IOException("Invalid text size");
        byte[] bytes=new byte[length];input.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream output=new DataOutputStream(bytes);
            output.writeInt(MAGIC);output.writeLong(revision);output.writeLong(nextId);output.writeInt(replies.size());
            for(Reply reply:replies){output.writeLong(reply.id);writeText(output,reply.label);writeText(output,reply.text);}
            output.flush();if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("Templates too large");
            return bytes.toByteArray();
        } catch(IOException impossible){throw new IllegalStateException("Template encoding failed",impossible);}
    }
    public static MontogramQuickReplyData decode(byte[] bytes) throws IOException {
        if(bytes==null || bytes.length>MAX_BYTES)throw new IOException("Invalid template file size");
        DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            if(input.readInt()!=MAGIC)throw new IOException("Unknown template format");
            long revision=input.readLong(),nextId=input.readLong();int count=input.readInt();
            if(count<0 || count>MAX_COUNT)throw new IOException("Invalid template count");
            ArrayList<Reply> replies=new ArrayList<>(count);
            for(int i=0;i<count;i++) {
                long id=input.readLong();String label=readText(input,MAX_LABEL),text=readText(input,MAX_TEXT);
                Reply reply=new Reply(id,label,text);
                if(!label.equals(reply.label))throw new IOException("Noncanonical label");
                replies.add(reply);
            }
            if(input.available()!=0)throw new IOException("Trailing template data");
            return new MontogramQuickReplyData(revision,nextId,replies);
        } catch(IllegalArgumentException invalid){throw new IOException("Invalid template data",invalid);}
    }
}
