/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;
import java.security.GeneralSecurityException;
import javax.crypto.SecretKey;
/** M15 format compatibility adapter. */
final class MontogramQuickReplyCipher {
    static final int MAX_ENVELOPE_BYTES=MontogramQuickReplyData.MAX_BYTES+46;
    private MontogramQuickReplyCipher(){}
    static byte[] seal(SecretKey key,int account,long self,byte[] data)throws GeneralSecurityException{return MontogramLocalCipher.seal(MontogramLocalFeature.QUICK_REPLIES,key,account,self,data);}
    static byte[] open(SecretKey key,int account,long self,byte[] data)throws GeneralSecurityException{return MontogramLocalCipher.open(MontogramLocalFeature.QUICK_REPLIES,key,account,self,data);}
}
