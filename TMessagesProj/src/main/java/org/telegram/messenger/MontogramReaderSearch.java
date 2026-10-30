/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;
import java.util.ArrayList;
/** Bounded local simple-case search; offsets remain UTF-16 and never split a code point. */
public final class MontogramReaderSearch {
    public static final int MAX_TEXT=32768,MAX_QUERY=128,MAX_HITS=256;
    private MontogramReaderSearch(){}
    public static void validate(String text,int limit){
        if(text==null||text.length()>limit)throw new IllegalArgumentException("Text limit");
        for(int i=0;i<text.length();i++)if(Character.isHighSurrogate(text.charAt(i))){if(++i>=text.length()||!Character.isLowSurrogate(text.charAt(i)))throw new IllegalArgumentException("Invalid Unicode");}else if(Character.isLowSurrogate(text.charAt(i)))throw new IllegalArgumentException("Invalid Unicode");
    }
    public static final class Hits {
        public final int[] offsets;public final boolean more;public final int width;
        private Hits(int[] offsets,boolean more,int width){this.offsets=offsets;this.more=more;this.width=width;}
    }
    public static Hits find(String text,String query){
        validate(text,MAX_TEXT);validate(query,MAX_QUERY);
        ArrayList<Integer> result=new ArrayList<>();boolean more=false;
        if(!query.isEmpty())for(int i=0;i+query.length()<=text.length();i+=Character.charCount(text.codePointAt(i))){
            int end=i+query.length();
            if(end<text.length()&&Character.isLowSurrogate(text.charAt(end)))continue;
            if(text.regionMatches(true,i,query,0,query.length())){if(result.size()==MAX_HITS){more=true;break;}result.add(i);}
        }
        int[] positions=new int[result.size()];for(int i=0;i<positions.length;i++)positions[i]=result.get(i);
        return new Hits(positions,more,query.length());
    }
}
