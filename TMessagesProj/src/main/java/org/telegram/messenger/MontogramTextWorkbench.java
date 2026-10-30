/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;

/** Manual, bounded own-input operations. No clipboard reads, storage, network or send APIs. */
public final class MontogramTextWorkbench {
    public static final int MAX_OUTPUT_UTF16 = 32768;
    public static final int MAX_PARTS = 256;
    private MontogramTextWorkbench() {}

    /** UTF-16 Telegram limit, code-point boundaries, preferred newline; exact join round trip. */
    public static ArrayList<String> split(String input, int limit) {
        MontogramTextTools.validate(input);
        if (limit < 64 || limit > MontogramTextTools.MAX_UTF16) throw new IllegalArgumentException("Invalid part limit");
        ArrayList<String> parts = new ArrayList<>();
        for (int start = 0; start < input.length();) {
            int end = Math.min(start + limit, input.length());
            if (end < input.length()) {
                if (Character.isLowSurrogate(input.charAt(end))) end--;
                int newline = input.lastIndexOf('\n', end - 1);
                if (newline >= start + limit / 2) end = newline + 1;
                // Keep CRLF together as well as surrogate pairs.
                if (end < input.length() && end > start && input.charAt(end - 1) == '\r' && input.charAt(end) == '\n') end--;
            }
            if (end <= start || parts.size() == MAX_PARTS) throw new IllegalArgumentException("Too many parts");
            parts.add(input.substring(start, end)); start = end;
        }
        return parts;
    }

    public static final class Comparison {
        public final int commonPrefixPoints, commonSuffixPoints;
        public final String removed, inserted;
        private Comparison(int prefix, int suffix, String removed, String inserted) {
            commonPrefixPoints = prefix; commonSuffixPoints = suffix;
            this.removed = removed; this.inserted = inserted;
        }
    }

    /** One changed region after common CP prefix/suffix, NOT a minimal edit script or LCS. */
    public static Comparison compare(String before, String after) {
        MontogramTextTools.validate(before); MontogramTextTools.validate(after);
        int a = 0, b = 0, prefix = 0, suffix = 0, ae = before.length(), be = after.length();
        while (a < ae && b < be && before.codePointAt(a) == after.codePointAt(b)) {
            a += Character.charCount(before.codePointAt(a)); b += Character.charCount(after.codePointAt(b)); prefix++;
        }
        while (ae > a && be > b && before.codePointBefore(ae) == after.codePointBefore(be)) {
            ae -= Character.charCount(before.codePointBefore(ae)); be -= Character.charCount(after.codePointBefore(be)); suffix++;
        }
        return new Comparison(prefix, suffix, before.substring(a, ae), after.substring(b, be));
    }

    public static final class UrlParts {
        public final String scheme, host, path;
        public final int port, parameterCount;
        public final boolean userInfoHidden, fragmentHidden;
        public final String[] parameterNames;
        private UrlParts(String scheme, String host, int port, String path, int count, String[] names, boolean userInfo, boolean fragment) {
            this.scheme=scheme; this.host=host; this.port=port; this.path=path;
            parameterCount=count; parameterNames=names; userInfoHidden=userInfo; fragmentHidden=fragment;
        }
    }

    /** HTTP(S) structure only. Userinfo, fragment, ALL query values are excluded from result. */
    public static UrlParts inspectUrl(String input) {
        MontogramTextTools.validate(input);
        try {
            URI uri = new URI(input);
            String scheme = uri.getScheme(), authority = uri.getRawAuthority();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) || authority == null || authority.isEmpty()) throw new IllegalArgumentException("Expected HTTP URL");
            int at = authority.lastIndexOf('@'); boolean userInfo = at >= 0;
            String hostPort = authority.substring(at + 1), host; String portText = null;
            if (hostPort.startsWith("[")) {
                int close = hostPort.indexOf(']');
                if (close < 0) throw new IllegalArgumentException("Invalid IPv6 host");
                host = hostPort.substring(0, close + 1);
                if (close + 1 < hostPort.length()) {
                    if (hostPort.charAt(close + 1) != ':') throw new IllegalArgumentException("Invalid authority");
                    portText = hostPort.substring(close + 2);
                }
                if (new URI("https://" + host).getHost() == null) throw new IllegalArgumentException("Invalid IPv6 host");
            } else {
                int colon = hostPort.lastIndexOf(':');
                host = colon < 0 ? hostPort : hostPort.substring(0, colon);
                if (colon >= 0) portText = hostPort.substring(colon + 1);
                if (host.indexOf('%') >= 0 || host.isEmpty()) throw new IllegalArgumentException("Invalid host");
                host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES);
                if (new URI("https://" + host).getHost() == null) throw new IllegalArgumentException("Invalid host");
            }
            int port = -1;
            if (portText != null) {
                if (portText.isEmpty() || portText.length() > 5) throw new IllegalArgumentException("Invalid port");
                for (int i=0;i<portText.length();i++) if (portText.charAt(i)<'0'||portText.charAt(i)>'9') throw new IllegalArgumentException("Invalid port");
                port=Integer.parseInt(portText); if(port>65535) throw new IllegalArgumentException("Invalid port");
            }
            String query=uri.getRawQuery(); ArrayList<String> names=new ArrayList<>(); int count=0;
            if (query!=null && !query.isEmpty()) {
                int start=0;
                while(start<=query.length()) {
                    int end=query.indexOf('&',start);if(end<0)end=query.length();
                    int equal=query.indexOf('=',start);if(equal<0||equal>end)equal=end;
                    if(names.size()<MontogramTextTools.MAX_FINDINGS) {
                        String key=query.substring(start,equal);
                        names.add(key.matches("[A-Za-z_][A-Za-z0-9_.-]{0,39}")?key:"[hidden]");
                    }
                    count++;if(end==query.length())break;start=end+1;
                }
            }
            return new UrlParts(scheme.toLowerCase(java.util.Locale.ROOT),host,port,uri.getRawPath(),count,names.toArray(new String[0]),userInfo,uri.getRawFragment()!=null);
        } catch(URISyntaxException invalid) { throw new IllegalArgumentException("Invalid HTTP URL"); }
    }

    public static final class ScriptInspection {
        public final int[] firstPositions, lastPositions, scripts;
        public final int omitted;
        private ScriptInspection(int[] first,int[] last,int[] scripts,int omitted) {
            firstPositions=first;lastPositions=last;this.scripts=scripts;this.omitted=omitted;
        }
    }
    /** Finite Latin/Cyrillic/Greek range heuristic. No homoglyph or safety verdict. */
    public static ScriptInspection inspectScripts(String input) {
        MontogramTextTools.validate(input);
        int[] first=new int[128],last=new int[128],masks=new int[128];int total=0,position=0,start=0,mask=0;
        for(int offset=0;offset<input.length();) {
            int cp=input.codePointAt(offset);offset+=Character.charCount(cp);position++;
            if(wordPart(cp)) {
                if(start==0)start=position;
                if(Character.isLetter(cp))mask|=script(cp);
            } else {
                if(Integer.bitCount(mask)>1) {if(total<128){first[total]=start;last[total]=position-1;masks[total]=mask;}total++;}
                start=0;mask=0;
            }
        }
        if(Integer.bitCount(mask)>1) {if(total<128){first[total]=start;last[total]=position;masks[total]=mask;}total++;}
        int length=Math.min(total,128);
        return new ScriptInspection(Arrays.copyOf(first,length),Arrays.copyOf(last,length),Arrays.copyOf(masks,length),total-length);
    }
    private static boolean wordPart(int cp) {
        int type=Character.getType(cp);
        return Character.isLetterOrDigit(cp)||type==Character.NON_SPACING_MARK||type==Character.COMBINING_SPACING_MARK||type==Character.ENCLOSING_MARK;
    }
    private static int script(int cp) {
        if(cp>='A'&&cp<='Z'||cp>='a'&&cp<='z'||cp>=0x00c0&&cp<=0x024f||cp>=0x1e00&&cp<=0x1eff) return 1;
        if(cp>=0x0400&&cp<=0x052f||cp>=0x1c80&&cp<=0x1c8f||cp>=0xa640&&cp<=0xa69f)return 2;
        if(cp>=0x0370&&cp<=0x03ff||cp>=0x1f00&&cp<=0x1fff)return 4;
        return 0;
    }
    public static String supportReport(String version,int sdk,String[] abis) {
        if(version==null||!version.matches("[A-Za-z0-9._-]{1,80}")||sdk<1||sdk>1000||abis==null||abis.length>16)throw new IllegalArgumentException("Invalid public build metadata");
        StringBuilder report=new StringBuilder("Montogram\nversion=").append(version).append("\nandroid_api=").append(sdk).append("\nabis=");
        for(int i=0;i<abis.length;i++) {
            if(abis[i]==null||!abis[i].matches("[A-Za-z0-9_-]{1,32}"))throw new IllegalArgumentException("Invalid ABI");
            if(i>0)report.append(',');report.append(abis[i]);
        }
        return report.append("\nreport_schema=1\nNo account data, logs or paths included.").toString();
    }
}
