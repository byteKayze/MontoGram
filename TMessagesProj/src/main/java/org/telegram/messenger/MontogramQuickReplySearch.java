/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Bounded literal NFC/ROOT search. Output always contains original row indices. */
public final class MontogramQuickReplySearch {
    private MontogramQuickReplySearch() {}
    private static String normalized(String text) {
        return Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
    }
    private static boolean validQuery(String text) {
        if (text == null || text.length() > 256) return false;
        int points = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
            } else if (Character.isLowSurrogate(c)) return false;
            if (++points > 128) return false;
        }
        return true;
    }
    public static int[] order(List<MontogramQuickReplyData.Reply> rows, String query) {
        if (rows == null || rows.size() > MontogramQuickReplyData.MAX_COUNT || !validQuery(query)) return new int[0];
        String needle = normalized(query);
        int[] ranks = new int[rows.size()];
        int count = 0;
        for (int i = 0; i < rows.size(); i++) {
            MontogramQuickReplyData.Reply row = rows.get(i);
            if (row == null) { ranks[i] = 4; continue; }
            if (needle.isEmpty()) ranks[i] = 0;
            else {
                String label = normalized(row.label);
                ranks[i] = label.equals(needle) ? 0 : label.startsWith(needle) ? 1
                        : label.contains(needle) ? 2 : normalized(row.text).contains(needle) ? 3 : 4;
            }
            if (ranks[i] < 4) count++;
        }
        int[] indices = new int[count];
        int next = 0;
        for (int rank = 0; rank < 4; rank++)
            for (int i = 0; i < ranks.length; i++) if (ranks[i] == rank) indices[next++] = i;
        return indices;
    }
}
