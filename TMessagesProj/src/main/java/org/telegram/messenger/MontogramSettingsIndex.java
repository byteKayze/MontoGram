/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Immutable, Android-free settings navigation index. Search never executes an action. */
public final class MontogramSettingsIndex {
    public static final int MAX_QUERY_CODE_POINTS = 128;
    private static final int MAX_NORMALIZED_QUERY = 256;
    private static final int MAX_INDEX_TEXT = 8192;

    public enum Category {
        APPEARANCE(1001), CHAT_TOOLS(1002), BACKUP(1003), PERFORMANCE(1004), ABOUT(1005), NAVIGATION(1006);
        public final int navigationId;
        Category(int navigationId) { this.navigationId = navigationId; }
        public static Category fromNavigationId(int id) {
            for (Category value : values()) if (value.navigationId == id) return value;
            return null;
        }
    }

    public static final class Entry {
        public final int id;
        public final Category category;
        public final String title, description, categoryLabel;
        private final String normalized;
        public Entry(int id, Category category, String title, String description, String categoryLabel, String aliases) {
            if (id < 1 || id > 999 || category == null || title == null || title.trim().isEmpty()
                    || description == null || categoryLabel == null || aliases == null)
                throw new IllegalArgumentException("Invalid settings descriptor");
            if (!bounded(title, MAX_INDEX_TEXT) || !bounded(description, MAX_INDEX_TEXT)
                    || !bounded(categoryLabel, MAX_INDEX_TEXT) || !bounded(aliases, MAX_INDEX_TEXT))
                throw new IllegalArgumentException("Descriptor field too long");
            String text = title + " " + description + " " + categoryLabel + " " + aliases;
            if (!bounded(text, MAX_INDEX_TEXT)) throw new IllegalArgumentException("Descriptor too long");
            normalized = normalize(text);
            if (!bounded(normalized, MAX_INDEX_TEXT)) throw new IllegalArgumentException("Normalized descriptor too long");
            this.id = id;
            this.category = category;
            this.title = title;
            this.description = description;
            this.categoryLabel = categoryLabel;
        }
    }

    private final List<Entry> entries;
    public MontogramSettingsIndex(List<Entry> source) {
        if (source == null || source.size() > 128) throw new IllegalArgumentException("Invalid index size");
        Set<Integer> ids = new HashSet<>();
        ArrayList<Entry> copy = new ArrayList<>();
        for (Entry entry : source) {
            if (entry == null || !ids.add(entry.id)) throw new IllegalArgumentException("Duplicate or missing descriptor");
            copy.add(entry);
        }
        entries = Collections.unmodifiableList(copy);
    }

    public List<Entry> entries(Category category) {
        if (category == null) return entries;
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : entries) if (entry.category == category) result.add(entry);
        return Collections.unmodifiableList(result);
    }

    public Entry find(int id) {
        for (Entry entry : entries) if (entry.id == id) return entry;
        return null;
    }

    public List<Entry> search(String query) {
        if (query == null) return entries;
        if (!bounded(query, MAX_QUERY_CODE_POINTS)) return Collections.emptyList();
        boolean blank = true;
        for (int offset = 0; offset < query.length();) {
            int cp = query.codePointAt(offset);
            offset += Character.charCount(cp);
            if (!Character.isWhitespace(cp) && !Character.isSpaceChar(cp)) { blank = false; break; }
        }
        if (blank) return entries;
        String normalized = normalize(query);
        if (normalized.isEmpty() || !bounded(normalized, MAX_NORMALIZED_QUERY)) return Collections.emptyList();
        String[] words = normalized.split(" ");
        ArrayList<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            boolean matches = true;
            for (String word : words) if (!entry.normalized.contains(word)) { matches = false; break; }
            if (matches) result.add(entry);
        }
        return Collections.unmodifiableList(result);
    }

    /** End offset within budget, or -1 for malformed/split source UTF-16; no allocations. */
    public static int insertionEnd(CharSequence source, int start, int end,
            CharSequence destination, int replaceStart, int replaceEnd) {
        if (source == null || destination == null || start < 0 || end < start || end > source.length()
                || replaceStart < 0 || replaceEnd < replaceStart || replaceEnd > destination.length())
            throw new IllegalArgumentException("Invalid insertion range");
        if (start == end) return end; // Always permit deletion.
        if (!isCodePointBoundary(source, start) || !isCodePointBoundary(source, end)) return -1;
        int remaining = MAX_QUERY_CODE_POINTS
            - Character.codePointCount(destination, 0, replaceStart)
            - Character.codePointCount(destination, replaceEnd, destination.length());
        int offset = start;
        while (offset < end && remaining > 0) {
            char current = source.charAt(offset++);
            if (Character.isHighSurrogate(current)) {
                if (offset >= end || !Character.isLowSurrogate(source.charAt(offset))) return -1;
                offset++;
            } else if (Character.isLowSurrogate(current)) return -1;
            remaining--;
        }
        return offset;
    }

    /** Android InputFilter-compatible result. Invalid edits preserve the replaced text. */
    public static CharSequence filterInsertion(CharSequence source, int start, int end,
            CharSequence destination, int replaceStart, int replaceEnd) {
        int allowed = insertionEnd(source, start, end, destination, replaceStart, replaceEnd);
        if (allowed < 0 || !isCodePointBoundary(destination, replaceStart)
                || !isCodePointBoundary(destination, replaceEnd))
            return destination.subSequence(replaceStart, replaceEnd);
        return allowed == end ? null : source.subSequence(start, allowed);
    }

    public static boolean isCodePointBoundary(CharSequence text, int offset) {
        return text != null && offset >= 0 && offset <= text.length() &&
            (offset == 0 || offset == text.length() || !Character.isHighSurrogate(text.charAt(offset - 1))
                || !Character.isLowSurrogate(text.charAt(offset)));
    }

    private static boolean bounded(String text, int points) {
        return text.length() <= points * 2 && text.codePointCount(0, text.length()) <= points;
    }

    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder result = new StringBuilder();
        boolean space = false;
        for (int offset = 0; offset < decomposed.length();) {
            int cp = decomposed.codePointAt(offset);
            offset += Character.charCount(cp);
            int type = Character.getType(cp);
            if (type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) continue;
            if (Character.isLetterOrDigit(cp)) {
                if (space && result.length() > 0) result.append(' ');
                result.appendCodePoint(cp);
                space = false;
            } else space = true;
        }
        return result.toString();
    }
}
