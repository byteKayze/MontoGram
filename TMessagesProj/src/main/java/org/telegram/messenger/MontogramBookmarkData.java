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

/** Immutable references and user-authored labels; no message content or persistence.
 * A future store must bind ownership and protect labels. Eligibility is checked
 * at capture/open time, never inferred from this codec accepting a reference.
 */
public final class MontogramBookmarkData {
    public static final int MAX_COUNT = 128, MAX_LABEL = 64, MAX_BYTES = 32768;
    private static final int MAGIC = 0x4d424b31; // MBK1

    public static final class Entry {
        public final long id, dialogId;
        public final int messageId;
        public final String label;

        private Entry(long id, long dialogId, int messageId, String label) {
            if (id <= 0 || dialogId == 0 || messageId <= 0) {
                throw new IllegalArgumentException("Invalid bookmark reference");
            }
            this.id = id;
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.label = normalize(label);
        }
    }

    public final long revision;
    public final List<Entry> entries;
    private final long nextId;

    private MontogramBookmarkData(long revision, long nextId, List<Entry> entries) {
        if (revision < 0 || nextId <= 0 || nextId - 1 > revision || entries.size() > MAX_COUNT ||
                (revision == 0 && (nextId != 1 || !entries.isEmpty()))) {
            throw new IllegalArgumentException("Invalid bookmark state");
        }
        HashSet<Long> ids = new HashSet<>();
        HashSet<String> references = new HashSet<>();
        for (Entry entry : entries) {
            if (entry.id >= nextId || !ids.add(entry.id) ||
                    !references.add(entry.dialogId + ":" + entry.messageId)) {
                throw new IllegalArgumentException("Duplicate or invalid bookmark identity");
            }
        }
        this.revision = revision;
        this.nextId = nextId;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public static MontogramBookmarkData empty() {
        return new MontogramBookmarkData(0, 1, Collections.emptyList());
    }

    public Entry find(long dialogId, int messageId) {
        for (Entry entry : entries) {
            if (entry.dialogId == dialogId && entry.messageId == messageId) return entry;
        }
        return null;
    }

    public Entry get(long id) {
        for (Entry entry : entries) if (entry.id == id) return entry;
        return null;
    }

    private long nextRevision() {
        if (revision == Long.MAX_VALUE) throw new IllegalArgumentException("Revision exhausted");
        return revision + 1;
    }

    public MontogramBookmarkData upsert(long dialogId, int messageId, String label) {
        Entry previous = find(dialogId, messageId);
        Entry replacement = new Entry(previous == null ? nextId : previous.id, dialogId, messageId, label);
        if (previous != null && previous.label.equals(replacement.label)) return this;
        if (previous == null && (entries.size() == MAX_COUNT || nextId == Long.MAX_VALUE)) {
            throw new IllegalArgumentException("Bookmark limit reached");
        }
        ArrayList<Entry> copy = new ArrayList<>(entries);
        if (previous == null) copy.add(replacement);
        else copy.set(entries.indexOf(previous), replacement);
        return new MontogramBookmarkData(nextRevision(), previous == null ? nextId + 1 : nextId, copy);
    }

    public MontogramBookmarkData rename(long id, String label) {
        Entry previous = get(id);
        if (previous == null) throw new IllegalArgumentException("Unknown bookmark");
        return upsert(previous.dialogId, previous.messageId, label);
    }

    public MontogramBookmarkData remove(long id) {
        Entry previous = get(id);
        if (previous == null) throw new IllegalArgumentException("Unknown bookmark");
        ArrayList<Entry> copy = new ArrayList<>(entries);
        copy.remove(previous);
        return new MontogramBookmarkData(nextRevision(), nextId, copy);
    }

    public MontogramBookmarkData clear() {
        return entries.isEmpty() ? this : new MontogramBookmarkData(nextRevision(), nextId, Collections.emptyList());
    }

    /** Optional label: <=64 UTF-16 units before trimming; Unicode outer spaces
     * are trimmed. Controls, bidi overrides/isolates and malformed surrogates
     * are rejected, never replaced. Emoji ZWJ and ordinary RTL text are valid.
     */
    private static String normalize(String label) {
        if (label == null || label.length() > MAX_LABEL) throw new IllegalArgumentException("Invalid bookmark label");
        try { utf8(label); }
        catch (CharacterCodingException invalid) { throw new IllegalArgumentException("Invalid Unicode label", invalid); }
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (Character.isISOControl(c) || c == '\u061c' || c == '\u200e' || c == '\u200f' ||
                    c >= '\u202a' && c <= '\u202e' || c >= '\u2066' && c <= '\u2069') {
                throw new IllegalArgumentException("Control character in bookmark label");
            }
        }
        int start = 0, end = label.length();
        while (start < end && space(label.codePointAt(start))) start += Character.charCount(label.codePointAt(start));
        while (end > start && space(label.codePointBefore(end))) end -= Character.charCount(label.codePointBefore(end));
        return label.substring(start, end);
    }

    private static boolean space(int c) { return Character.isWhitespace(c) || Character.isSpaceChar(c); }

    private static byte[] utf8(String value) throws CharacterCodingException {
        ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        return bytes;
    }

    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(MAGIC);
        output.writeLong(revision);
        output.writeLong(nextId);
        output.writeInt(entries.size());
        for (Entry entry : entries) {
            output.writeLong(entry.id);
            output.writeLong(entry.dialogId);
            output.writeInt(entry.messageId);
            byte[] label = utf8(entry.label);
            output.writeInt(label.length);
            output.write(label);
        }
        output.flush();
        if (bytes.size() > MAX_BYTES) throw new IOException("Bookmark payload too large");
        return bytes.toByteArray();
    }

    public static MontogramBookmarkData decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Invalid bookmark payload size");
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        try {
            if (input.readInt() != MAGIC) throw new IOException("Unknown bookmark format");
            long revision = input.readLong(), nextId = input.readLong();
            int count = input.readInt();
            if (count < 0 || count > MAX_COUNT) throw new IOException("Invalid bookmark count");
            ArrayList<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                long id = input.readLong(), dialogId = input.readLong();
                int messageId = input.readInt(), length = input.readInt();
                if (length < 0 || length > MAX_LABEL * 3 || length > input.available()) {
                    throw new IOException("Invalid bookmark label length");
                }
                byte[] labelBytes = new byte[length];
                input.readFully(labelBytes);
                String label = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(labelBytes)).toString();
                Entry entry = new Entry(id, dialogId, messageId, label);
                if (!label.equals(entry.label)) throw new IOException("Noncanonical bookmark label");
                entries.add(entry);
            }
            if (input.available() != 0) throw new IOException("Trailing bookmark data");
            return new MontogramBookmarkData(revision, nextId, entries);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid bookmark data", invalid);
        }
    }
}
