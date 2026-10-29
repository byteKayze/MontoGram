/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Typed compatibility adapter; the native logout bracket also clears all local feature stores. */
final class MontogramQuickReplyMemory {
    private static final MontogramQuickReplyMemory GLOBAL=new MontogramQuickReplyMemory(MontogramLocalOwners.QUICK_REPLIES,true);
    static MontogramQuickReplyMemory global(){return GLOBAL;}
    private final MontogramOwnerMemory<MontogramQuickReplyData> memory;
    private final boolean global;
    MontogramQuickReplyMemory(int accounts){this(new MontogramOwnerMemory<>(accounts,MontogramQuickReplyData::empty),false);}
    private MontogramQuickReplyMemory(MontogramOwnerMemory<MontogramQuickReplyData> memory,boolean global){this.memory=memory;this.global=global;}
    static final class Snapshot {
        private final MontogramQuickReplyMemory owner;
        private final MontogramOwnerMemory.Snapshot<MontogramQuickReplyData> value;
        final int account;final long self;final MontogramQuickReplyData data;
        private Snapshot(MontogramQuickReplyMemory owner,MontogramOwnerMemory.Snapshot<MontogramQuickReplyData> value){this.owner=owner;this.value=value;account=value.account;self=value.self;data=value.data;}
    }
    static final class ClearToken {
        private final MontogramQuickReplyMemory owner;
        private final MontogramOwnerMemory.ClearToken local;
        private final MontogramLocalOwners.ClearTokens all;
        private ClearToken(MontogramQuickReplyMemory owner,MontogramOwnerMemory.ClearToken local,MontogramLocalOwners.ClearTokens all){this.owner=owner;this.local=local;this.all=all;}
    }
    private Snapshot wrap(MontogramOwnerMemory.Snapshot<MontogramQuickReplyData> value){return value==null?null:new Snapshot(this,value);}
    private MontogramOwnerMemory.Snapshot<MontogramQuickReplyData> unwrap(Snapshot value){return value!=null&&value.owner==this?value.value:null;}
    Snapshot bind(int account,long self){return wrap(memory.bind(account,self));}
    boolean isCurrent(Snapshot value){return memory.isCurrent(unwrap(value));}
    Snapshot save(Snapshot previous,long id,String label,String text){if(id<0)throw new IllegalArgumentException("Invalid template ID");MontogramOwnerMemory.Snapshot<MontogramQuickReplyData> value=memory.mutate(unwrap(previous),v->id==0?v.add(label,text):v.edit(id,label,text));return value!=null&&value==unwrap(previous)?previous:wrap(value);}
    Snapshot remove(Snapshot previous,long id){return wrap(memory.mutate(unwrap(previous),v->v.delete(id)));}
    Snapshot replace(Snapshot previous,MontogramQuickReplyData data){return wrap(memory.replace(unwrap(previous),data));}
    void revoke(Snapshot previous){memory.revoke(unwrap(previous));}
    void revokeAll(){if(global)MontogramLocalOwners.revokeAll();else memory.revokeAll();}
    public ClearToken beginClear(int account){
        if(global){MontogramLocalOwners.ClearTokens all=MontogramLocalOwners.beginClear(account);return all==null?null:new ClearToken(this,null,all);}
        MontogramOwnerMemory.ClearToken token=memory.beginClear(account);return token==null?null:new ClearToken(this,token,null);
    }
    public boolean finishClear(ClearToken token,boolean completed){if(token==null||token.owner!=this)return false;return token.all!=null?MontogramLocalOwners.finishClear(token.all,completed):memory.finishClear(token.local,completed);}
}
