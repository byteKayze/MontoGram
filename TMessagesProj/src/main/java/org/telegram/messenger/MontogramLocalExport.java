/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Portable user-requested fields only: no owner IDs, source revisions or crypto metadata. */
public final class MontogramLocalExport {
    public static final int MAX_BYTES=2*1024*1024;
    private MontogramLocalExport(){}
    private static void quote(StringBuilder out,String text){
        out.append('"');
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='"'||c=='\\')out.append('\\').append(c);
            else if(c<0x20)out.append(String.format(java.util.Locale.ROOT,"\\u%04x",(int)c));
            else out.append(c);
        }
        out.append('"');
    }
    private static byte[] finish(StringBuilder out){
        if(out.length()>MAX_BYTES)throw new IllegalArgumentException("Export exceeds byte limit");
        byte[] bytes=out.toString().getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_BYTES){java.util.Arrays.fill(bytes,(byte)0);throw new IllegalArgumentException("Export exceeds byte limit");}
        return bytes;
    }
    public static byte[] notes(List<MontogramNoteData.Entry> rows){
        if(rows==null||rows.size()>MontogramNoteData.MAX_COUNT)throw new IllegalArgumentException("Invalid notes");
        StringBuilder out=new StringBuilder("{\"format\":\"montogram.notes\",\"schema\":1,\"notes\":[");
        for(int i=0;i<rows.size();i++){
            MontogramNoteData.Entry row=rows.get(i);if(i>0)out.append(',');
            out.append("{\"dialog_id\":").append(row.dialogId).append(",\"text\":");quote(out,row.text);out.append('}');
        }
        return finish(out.append("]}\n"));
    }
    public static byte[] bookmarks(List<MontogramBookmarkData.Entry> rows){
        if(rows==null||rows.size()>MontogramBookmarkData.MAX_COUNT)throw new IllegalArgumentException("Invalid bookmarks");
        StringBuilder out=new StringBuilder("{\"format\":\"montogram.bookmark-labels\",\"schema\":1,\"bookmarks\":[");
        for(int i=0;i<rows.size();i++){
            MontogramBookmarkData.Entry row=rows.get(i);if(i>0)out.append(',');
            out.append("{\"dialog_id\":").append(row.dialogId).append(",\"message_id\":").append(row.messageId).append(",\"label\":");quote(out,row.label);
            out.append(",\"folder\":");quote(out,row.folder);out.append('}');
        }
        return finish(out.append("]}\n"));
    }
    public static byte[] replies(List<MontogramQuickReplyData.Reply> rows){
        if(rows==null||rows.size()>MontogramQuickReplyData.MAX_COUNT)throw new IllegalArgumentException("Invalid replies");
        MontogramQuickReplyData portable=MontogramQuickReplyData.empty();
        for(MontogramQuickReplyData.Reply row:rows)portable=portable.add(row.label,row.text);
        return portable.encode(); // Fresh portable IDs/revision, never the source owner's IDs/revision.
    }
    public static String digest(byte[] bytes){
        try{
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out=new StringBuilder();for(byte value:hash)out.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return out.toString();
        }catch(java.security.GeneralSecurityException impossible){throw new IllegalStateException("SHA-256 unavailable");}
    }
}
