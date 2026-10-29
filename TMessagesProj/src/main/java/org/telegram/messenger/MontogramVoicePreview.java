/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** UI-owned transition latch; asynchronous rejection cannot clear a newer request. */
public final class MontogramVoicePreview {
    public static final class Ticket {
        private final int guid;
        private Ticket(int guid){this.guid=guid;}
    }
    private Ticket pending;
    public boolean isPending(){return pending!=null;}
    public Ticket begin(int guid){
        if(guid<0 || pending!=null)return null;
        return pending=new Ticket(guid);
    }
    public void reject(Ticket ticket){if(ticket!=null&&pending==ticket)pending=null;}
    public void complete(int guid){if(pending!=null&&pending.guid==guid)pending=null;}
    public void cancel(){pending=null;}

    /** Identity-only native snapshot. Never exposes recorder objects or file paths. */
    public static final class Owner {
        private final int account,guid;
        private final long dialog;
        private final Object document,file,controller;
        private final boolean v2;
        public Owner(int account,long dialog,int guid,Object document,Object file,Object controller,boolean v2){
            if(account<0||dialog==0||guid<0||document==null||!v2&&file==null||v2&&controller==null)
                throw new IllegalArgumentException("Invalid recording owner");
            this.account=account;this.dialog=dialog;this.guid=guid;this.document=document;
            this.file=file;this.controller=controller;this.v2=v2;
        }
        public boolean matches(int account,long dialog,int guid,Object document,Object file,Object controller,boolean v2){
            return this.account==account&&this.dialog==dialog&&this.guid==guid&&this.document==document
                &&this.file==file&&this.controller==controller&&this.v2==v2;
        }
    }
}
