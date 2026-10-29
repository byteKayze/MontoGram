/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Bounded own-input operations only: no views, storage, network, message or clipboard APIs. */
public final class MontogramTextTools {
    public static final int MAX_UTF16 = 8192;
    public static final int MAX_FINDINGS = 128;
    // US QWERTY ↔ Russian ЙЦУКЕН letter/punctuation keys, including shifted letter rows.
    // Number-row punctuation is deliberately outside this table and is preserved verbatim.
    private static final String EN = "`qwertyuiop[]asdfghjkl;'zxcvbnm,./~QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?";
    private static final String RU = "ёйцукенгшщзхъфывапролджэячсмитьбю.ЁЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ,";
    // Finite advisory table; not a Unicode security validator or normalizer.
    private static final int[] INVISIBLE = {
        0x0000, 0x0008, 0x0009, 0x000a, 0x000d, 0x001b, 0x007f, 0x0085,
        0x00a0, 0x00ad, 0x034f, 0x061c, 0x115f, 0x1160, 0x17b4, 0x17b5,
        0x180e, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004, 0x2005, 0x2006,
        0x2007, 0x2008, 0x2009, 0x200a, 0x200b, 0x200c, 0x200d, 0x200e,
        0x200f, 0x2028, 0x2029, 0x202a, 0x202b, 0x202c, 0x202d, 0x202e,
        0x202f, 0x205f, 0x2060, 0x2061, 0x2062, 0x2063, 0x2064, 0x2066,
        0x2067, 0x2068, 0x2069, 0x3000, 0x3164, 0xfe00, 0xfe01, 0xfe02,
        0xfe03, 0xfe04, 0xfe05, 0xfe06, 0xfe07, 0xfe08, 0xfe09, 0xfe0a,
        0xfe0b, 0xfe0c, 0xfe0d, 0xfe0e, 0xfe0f, 0xfeff, 0xffa0, 0xe0001
    };

    private MontogramTextTools() {}

    public static void validate(String text) {
        if (text == null || text.length() > MAX_UTF16) throw new IllegalArgumentException("Invalid input length");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                    throw new IllegalArgumentException("Unpaired surrogate");
            } else if (Character.isLowSurrogate(c)) throw new IllegalArgumentException("Unpaired surrogate");
        }
    }

    public static final class Stats {
        public final int utf16, codePoints, utf8Bytes, lines, tokenRuns;
        private Stats(int units, int points, int bytes, int lines, int tokens) {
            utf16 = units; codePoints = points; utf8Bytes = bytes; this.lines = lines; tokenRuns = tokens;
        }
    }

    /** Tokens are runs of Unicode letters/digits, with following combining marks attached.
     * Lines split on CRLF, CR, LF, NEL, LS or PS; empty input has zero lines. */
    public static Stats statistics(String text) {
        validate(text);
        int points = 0, tokens = 0, lines = text.isEmpty() ? 0 : 1, previous = -1;
        boolean inToken = false;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp); points++;
            int type = Character.getType(cp);
            boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            if (Character.isLetterOrDigit(cp)) {
                if (!inToken) tokens++;
                inToken = true;
            } else if (!mark || !inToken) inToken = false;
            if (cp == '\r' || cp == '\n' && previous != '\r' || cp == 0x85 || cp == 0x2028 || cp == 0x2029) lines++;
            previous = cp;
        }
        return new Stats(text.length(), points, text.getBytes(StandardCharsets.UTF_8).length, lines, tokens);
    }

    public static final class Inspection {
        /** One-based code-point positions (not UTF16 offsets); table entries only. */
        public final int[] positions, codePoints;
        public final int omitted;
        private Inspection(int[] positions, int[] codePoints, int omitted) {
            this.positions = positions; this.codePoints = codePoints; this.omitted = omitted;
        }
    }

    public static Inspection inspectInvisible(String text) {
        validate(text);
        int[] positions = new int[MAX_FINDINGS], points = new int[MAX_FINDINGS];
        int total = 0, position = 0;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp); position++;
            if (Arrays.binarySearch(INVISIBLE, cp) < 0) continue;
            if (total < MAX_FINDINGS) { positions[total] = position; points[total] = cp; }
            total++;
        }
        int count = Math.min(total, MAX_FINDINGS);
        return new Inspection(Arrays.copyOf(positions, count), Arrays.copyOf(points, count), total-count);
    }

    public static String convertKeyboard(String text, boolean englishToRussian) {
        validate(text);
        String from = englishToRussian ? EN : RU, to = englishToRussian ? RU : EN;
        StringBuilder output = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            int key = from.indexOf(cp);
            if (key < 0) output.appendCodePoint(cp); else output.append(to.charAt(key));
        }
        return output.toString();
    }

    public static final int UNCLOSED = 1, UNEXPECTED_CLOSER = 2;
    public static final class PairInspection {
        /** Ordered one-based code-point positions; zero expectedCloser means no opener. */
        public final int[] positions, codePoints, expectedClosers, kinds;
        public final int omitted;
        private PairInspection(int[] positions, int[] points, int[] expected, int[] kinds, int omitted) {
            this.positions = positions; codePoints = points; expectedClosers = expected;
            this.kinds = kinds; this.omitted = omitted;
        }
    }

    /** Structural heuristic, not a language/code parser. Brackets and quotes use separate
     * typed stacks. A quote first closes the expected top glyph, otherwise opens if known.
     * Apostrophes inside letter/digit/mark runs are ignored; escapes are not interpreted. */
    public static PairInspection inspectPairs(String text) {
        validate(text);
        byte[] issues = new byte[text.length()];
        int[] wanted = new int[text.length()];
        int[] bracketPositions = new int[text.length()], bracketClosers = new int[text.length()];
        int[] quotePositions = new int[text.length()], quoteClosers = new int[text.length()];
        int brackets = 0, quotes = 0, position = 0;
        for (int offset = 0; offset < text.length();) {
            int start = offset, cp = text.codePointAt(offset);
            offset += Character.charCount(cp); position++;
            int close = cp == '(' ? ')' : cp == '[' ? ']' : cp == '{' ? '}' : 0;
            if (close != 0) {
                bracketPositions[brackets] = position; bracketClosers[brackets++] = close;
            } else if (cp == ')' || cp == ']' || cp == '}') {
                if (brackets > 0 && bracketClosers[brackets-1] == cp) brackets--;
                else {
                    issues[position-1] = UNEXPECTED_CLOSER;
                    wanted[position-1] = brackets == 0 ? 0 : bracketClosers[brackets-1];
                }
            } else {
                if ((cp == '\'' || cp == 0x2019) && start > 0 && offset < text.length()
                        && wordPart(text.codePointBefore(start)) && wordPart(text.codePointAt(offset))) continue;
                if (quotes > 0 && quoteClosers[quotes-1] == cp) { quotes--; continue; }
                close = cp == '"' || cp == '\'' ? cp : cp == 0x00ab ? 0x00bb
                        : cp == 0x201c ? 0x201d : cp == 0x2018 ? 0x2019 : cp == 0x201e ? 0x201c : 0;
                if (close != 0) {
                    quotePositions[quotes] = position; quoteClosers[quotes++] = close;
                } else if (cp == 0x00bb || cp == 0x201d || cp == 0x2019) {
                    issues[position-1] = UNEXPECTED_CLOSER;
                    wanted[position-1] = quotes == 0 ? 0 : quoteClosers[quotes-1];
                }
            }
        }
        for (int i = 0; i < brackets; i++) {
            issues[bracketPositions[i]-1] = UNCLOSED; wanted[bracketPositions[i]-1] = bracketClosers[i];
        }
        for (int i = 0; i < quotes; i++) {
            issues[quotePositions[i]-1] = UNCLOSED; wanted[quotePositions[i]-1] = quoteClosers[i];
        }
        int[] positions = new int[MAX_FINDINGS], points = new int[MAX_FINDINGS];
        int[] expected = new int[MAX_FINDINGS], kinds = new int[MAX_FINDINGS];
        int total = 0; position = 0;
        for (int offset = 0; offset < text.length();) {
            int cp = text.codePointAt(offset); offset += Character.charCount(cp); position++;
            if (issues[position-1] == 0) continue;
            if (total < MAX_FINDINGS) {
                positions[total] = position; points[total] = cp;
                expected[total] = wanted[position-1]; kinds[total] = issues[position-1];
            }
            total++;
        }
        int count = Math.min(total, MAX_FINDINGS);
        return new PairInspection(Arrays.copyOf(positions, count), Arrays.copyOf(points, count),
                Arrays.copyOf(expected, count), Arrays.copyOf(kinds, count), total-count);
    }

    private static boolean wordPart(int cp) {
        int type = Character.getType(cp);
        return Character.isLetterOrDigit(cp) || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK;
    }

    public static final class LineDeduplication {
        public final String text;
        /** One-based original logical-line indices, in ascending order; CRLF is one boundary. */
        public final int[] removedLines;
        public final int removed, omitted;
        private LineDeduplication(String text, int[] lines, int removed) {
            this.text = text; removedLines = lines; this.removed = removed; omitted = removed-lines.length;
        }
    }

    /** Exact nonempty-line comparison. Empty lines stay; whitespace is significant.
     * CRLF/CR/LF/NEL/LS/PS delimit lines. Keep the first row AND its original separator;
     * remove each later equal row and its following separator, including a final row. */
    public static LineDeduplication deduplicateLines(String text) {
        validate(text);
        Set<String> seen = new HashSet<>();
        StringBuilder output = new StringBuilder(text.length());
        int[] lines = new int[MAX_FINDINGS];
        int removed = 0, line = 0;
        for (int start = 0; start < text.length();) {
            int end = start;
            while (end < text.length() && !lineBreak(text.charAt(end))) end++;
            int after = end;
            if (after < text.length()) {
                after++;
                if (text.charAt(end) == '\r' && after < text.length() && text.charAt(after) == '\n') after++;
            }
            String content = text.substring(start, end); line++;
            if (!content.isEmpty() && !seen.add(content)) {
                if (removed < MAX_FINDINGS) lines[removed] = line;
                removed++;
            } else output.append(text, start, after);
            start = after;
        }
        return new LineDeduplication(output.toString(), Arrays.copyOf(lines, Math.min(removed, MAX_FINDINGS)), removed);
    }

    private static boolean lineBreak(char c) {
        return c == '\r' || c == '\n' || c == 0x85 || c == 0x2028 || c == 0x2029;
    }
}
