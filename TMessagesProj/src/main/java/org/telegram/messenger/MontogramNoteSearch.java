/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.text.Normalizer;
import java.util.Locale;

/** NFC + ROOT-lowercase substring, not linguistic/caseless/regex search. No retained index. */
public final class MontogramNoteSearch {
    private MontogramNoteSearch() {}
    private static boolean valid(String text, int units, int points) {
        if (text == null || text.length() > units) return false;
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
            if (++count > points) return false;
        }
        return true;
    }
    private static String normalize(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }
    /** null means invalid, never the all-matching empty query. */
    public static String query(String text) {
        return valid(text, 256, 128) ? normalize(text) : null;
    }
    public static boolean matches(String note, String normalizedQuery) {
        return normalizedQuery != null && valid(normalizedQuery, 1024, 512)
                && valid(note, 4096, 4096) && (normalizedQuery.isEmpty() || normalize(note).contains(normalizedQuery));
    }
}
