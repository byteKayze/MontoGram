/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Facts originate from the owning ProfileActivity account, not the globally selected slot. */
public final class MontogramProfileIdentifiers {
    public static final int SECRET=1, TOPIC=2, NON_REGULAR=4, MY_PROFILE=8;
    public enum Kind { USER, CHAT }
    public static final class Row {
        public final Kind kind;
        public final long id;
        private Row(Kind kind,long id){this.kind=kind;this.id=id;}
        public String value(){return Long.toString(id);}
    }
    private MontogramProfileIdentifiers(){}
    public static Row resolve(boolean enabled,boolean accountActive,int openedAccount,int currentAccount,long openedSelf,long activeSelf,int mode,
            long userId,long chatId,long resolvedId,boolean knownPeer,boolean readable){
        if(!enabled||!accountActive||openedAccount<0||openedAccount!=currentAccount||openedSelf<=0||openedSelf!=activeSelf||(mode&~MY_PROFILE)!=0
                ||!knownPeer||!readable||userId<0||chatId<0||(userId==0)==(chatId==0))return null;
        long requested=userId!=0?userId:chatId;
        if(requested!=resolvedId)return null;
        boolean selfProfile=(mode&MY_PROFILE)!=0;
        if(selfProfile!=(userId!=0&&userId==activeSelf))return null;
        return new Row(userId!=0?Kind.USER:Kind.CHAT,requested);
    }
}
