/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** One personal-call request; consumed at the service-launch boundary, never persisted. */
public final class MontogramCallRequest {
    public static final long TIMEOUT_MS=60000;
    private final int account;
    private final long self,peer,created;
    private final boolean video;
    private int state; // 0 pending, 1 approved, 2 used, 3 cancelled.
    public MontogramCallRequest(int account,long self,long peer,boolean video,long created) {
        if(account<0||self<=0||peer<=0||created<0)throw new IllegalArgumentException("Invalid call context");
        this.account=account;this.self=self;this.peer=peer;this.video=video;this.created=created;
    }
    private boolean current(int account,long self,long peer,boolean video,boolean eligible,long now) {
        return eligible&&this.account==account&&this.self==self&&this.peer==peer&&this.video==video
            &&now>=created&&now-created<=TIMEOUT_MS;
    }
    public synchronized boolean approve(int account,long self,long peer,boolean video,boolean eligible,long now) {
        if(state!=0)return false;
        state=current(account,self,peer,video,eligible,now)?1:3;return state==1;
    }
    public synchronized boolean consume(int account,long self,long peer,boolean video,boolean eligible,long now) {
        if(state!=1)return false;
        state=current(account,self,peer,video,eligible,now)?2:3;return state==2;
    }
    public synchronized void cancel(){if(state!=2)state=3;}
    public synchronized boolean isApproved(){return state==1;}
}
