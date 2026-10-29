/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

/** Same predicate at capture, confirmation, and exact-message open. No copying or I/O. */
public final class MontogramBookmarkEligibility {
    private MontogramBookmarkEligibility(){}
    private static boolean peerAllowed(long dialogId,TLRPC.UserFull userFull,TLRPC.Chat chat){
        if(dialogId>0)return userFull!=null&&userFull.id==dialogId&&!userFull.noforwards_peer_enabled&&!userFull.noforwards_my_enabled;
        return dialogId<0&&chat!=null&&chat.id==-dialogId&&chat.migrated_to==null&&!chat.noforwards
            &&!(chat instanceof TLRPC.TL_chatForbidden)&&!(chat instanceof TLRPC.TL_channelForbidden);
    }
    private static boolean hasTextSpoilers(TLRPC.Message message){
        if(message.entities!=null)for(TLRPC.MessageEntity entity:message.entities)if(entity instanceof TLRPC.TL_messageEntitySpoiler)return true;
        return false;
    }
    public static boolean permits(MessageObject message,int account,long dialogId,int messageId,TLRPC.UserFull userFull,TLRPC.Chat chat){
        return peerAllowed(dialogId,userFull,chat)&&dialogId!=0&&!DialogObject.isEncryptedDialog(dialogId)
            &&message!=null&&message.currentAccount==account&&message.getDialogId()==dialogId&&message.getId()==messageId&&messageId>0
            &&message.messageOwner instanceof TLRPC.TL_message&&!message.deleted&&!message.scheduled&&!message.isDateObject
            &&message.getGroupId()==0&&!message.isSecret()&&!message.messageOwner.noforwards
            &&!hasTextSpoilers(message.messageOwner)&&message.type!=MessageObject.TYPE_PAID_MEDIA&&!message.isEphemeral()&&!message.isVoiceOnce()&&!message.isRoundOnce()
            &&!message.needDrawBluredPreview()&&!message.hasMediaSpoilers()&&!message.isExpiredStory()&&!message.isSponsored()
            &&message.messageOwner.ttl_period==0&&(message.messageOwner.media==null||message.messageOwner.media.ttl_seconds==0);
    }
}
