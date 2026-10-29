/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.util.Arrays;

/** One composer sticker transaction. Contains delivery metadata, never message text. */
public final class MontogramStickerRequest {
    public static final long TIMEOUT_MS=60000;
    public static final class Context {
        private final long[] facts;
        public Context(int account,long self,long dialog,long document,long draftRevision,
                       long replyPeer,int replyId,long topicPeer,int topicId,long quotePeer,int quoteId,
                       int quoteStart,int quoteEnd,int quoteOffset,int quoteLength,int quoteEditDate,int quoteTask,int quoteFlags,int quoteEntities,
                       long storyPeer,int storyId,int mode,long monoForumPeer,long sendAsPeer,long starsPrice,boolean scheduled) {
            if(account<0||self<=0||dialog==0||document<=0||draftRevision<0)
                throw new IllegalArgumentException("Invalid sticker context");
            facts=new long[]{account,self,dialog,document,draftRevision,replyPeer,replyId,topicPeer,topicId,quotePeer,quoteId,
                quoteStart,quoteEnd,quoteOffset,quoteLength,quoteEditDate,quoteTask,quoteFlags,quoteEntities,storyPeer,storyId,mode,monoForumPeer,sendAsPeer,starsPrice,scheduled?1:0,0,0};
        }
        private Context(long[] facts){this.facts=facts;}
        /** Exact effective native transaction values; no invented repeat policy. */
        public Context withSchedule(int date,int repeat){
            long[] copy=facts.clone();copy[26]=date;copy[27]=repeat;return new Context(copy);
        }
        public boolean matches(Context other){return other!=null&&Arrays.equals(facts,other.facts);}
    }
    private final Context expected;
    private final long created;
    private boolean finished;
    public MontogramStickerRequest(Context expected,long created) {
        if(expected==null||created<0)throw new IllegalArgumentException("Invalid sticker request");
        this.expected=expected;this.created=created;
    }
    public synchronized boolean consume(Context current,boolean eligible,long now) {
        if(finished)return false;
        finished=true;
        return eligible&&now>=created&&now-created<=TIMEOUT_MS&&expected.matches(current);
    }
    public synchronized void cancel(){finished=true;}
}
