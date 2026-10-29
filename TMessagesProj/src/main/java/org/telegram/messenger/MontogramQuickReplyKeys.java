/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;
import javax.crypto.SecretKey;
/** M15 alias compatibility adapter. */
final class MontogramQuickReplyKeys {
    private MontogramQuickReplyKeys(){}
    static SecretKey get(int account,long self,boolean create)throws Exception{return MontogramLocalKeys.get(MontogramLocalFeature.QUICK_REPLIES,account,self,create);}
    static void remove(int account,long self)throws Exception{MontogramLocalKeys.remove(MontogramLocalFeature.QUICK_REPLIES,account,self);}
}
