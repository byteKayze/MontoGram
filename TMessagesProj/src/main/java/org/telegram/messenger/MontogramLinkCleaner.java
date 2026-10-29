/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Explicit, local-only utility. Never follows links or decodes/re-encodes their values. */
public final class MontogramLinkCleaner {
    public static final int MAX_LENGTH = 4096;
    public enum Status { INVALID, UNCHANGED, PROTECTED, CLEANED }
    public static final class Result {
        public final String original, cleaned;
        public final Status status;
        public final int removed;
        private Result(String original, String cleaned, Status status, int removed) {
            this.original=original;this.cleaned=cleaned;this.status=status;this.removed=removed;
        }
    }
    private static final Set<String> TRACKING = new HashSet<>(Arrays.asList(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
        "fbclid", "gclid", "dclid", "msclkid", "igshid", "mc_cid", "mc_eid"));
    private static final Set<String> SENSITIVE = new HashSet<>(Arrays.asList(
        "signature", "sig", "sign", "token", "auth", "authorization", "access_token", "auth_token",
        "key", "api_key", "apikey", "hash", "hmac", "jwt", "expires", "expiry", "expire", "exp",
        "policy", "key-pair-id", "hdnts", "hdnea", "s", "se", "sp", "sv", "sr", "st",
        "skt", "ske", "skv", "skoid", "sktid", "sks", "skd"));
    private MontogramLinkCleaner() {}
    public static boolean fitsInsertion(int current, int replaced, int inserted) {
        return current>=0&&current<=MAX_LENGTH&&replaced>=0&&replaced<=current&&inserted>=0
            &&inserted<=MAX_LENGTH-(current-replaced);
    }
    private static Result retain(String original, Status status) {
        return new Result(original,original,status,0);
    }
    public static Result clean(String input) {
        if(input==null)return retain("",Status.INVALID);
        if(input.length()==0||input.length()>MAX_LENGTH)return retain(input,Status.INVALID);
        for(int i=0;i<input.length();i++) {
            char c=input.charAt(i);
            if(Character.isWhitespace(c)||Character.isISOControl(c)||Character.getType(c)==Character.FORMAT)
                return retain(input,Status.INVALID);
            if(Character.isHighSurrogate(c)) {
                if(i+1>=input.length()||!Character.isLowSurrogate(input.charAt(++i)))return retain(input,Status.INVALID);
            } else if(Character.isLowSurrogate(c))return retain(input,Status.INVALID);
        }
        final URI uri;
        try {uri=new URI(input);}catch(URISyntaxException ex){return retain(input,Status.INVALID);}
        String scheme=uri.getScheme();
        if(scheme==null||!(scheme.equalsIgnoreCase("https")||scheme.equalsIgnoreCase("http"))
                ||uri.isOpaque()||uri.getHost()==null||uri.getRawUserInfo()!=null||uri.getPort()==0||uri.getPort()>65535)
            return retain(input,Status.INVALID);
        String query=uri.getRawQuery();
        if(query==null||query.isEmpty())return retain(input,Status.UNCHANGED);
        if(query.indexOf(';')>=0)return retain(input,Status.PROTECTED);
        String[] parts=query.split("&",-1);
        // Inspect every key before removing anything. Never partially clean a signed query.
        for(String part:parts) {
            int equals=part.indexOf('=');String name=equals<0?part:part.substring(0,equals);
            final String decoded;
            try {decoded=URLDecoder.decode(name,"UTF-8").toLowerCase(Locale.ROOT);}
            catch(Exception ex){return retain(input,Status.PROTECTED);}
            for(int i=0;i<decoded.length();i++) {
                char c=decoded.charAt(i);
                if(!((c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='_'||c=='-'||c=='.'||c=='~'))
                    return retain(input,Status.PROTECTED);
            }
            if(SENSITIVE.contains(decoded)||decoded.startsWith("x-amz-")||decoded.startsWith("x-goog-")
                    ||decoded.startsWith("oauth_"))return retain(input,Status.PROTECTED);
        }
        StringBuilder kept=new StringBuilder(query.length());int removed=0,remaining=0;
        for(String part:parts) {
            int equals=part.indexOf('=');String name=equals<0?part:part.substring(0,equals);
            if(TRACKING.contains(name.toLowerCase(Locale.ROOT))) {removed++;continue;}
            if(remaining++>0)kept.append('&');kept.append(part);
        }
        if(removed==0)return retain(input,Status.UNCHANGED);
        int queryStart=input.indexOf('?');int fragmentStart=input.indexOf('#',queryStart);
        String cleaned=input.substring(0,queryStart)+(remaining==0?"":"?"+kept)
            +(fragmentStart<0?"":input.substring(fragmentStart));
        return new Result(input,cleaned,Status.CLEANED,removed);
    }
}
