/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.os.Looper;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Only the retained-data worker may use this; no key generation on reads. */
final class MontogramLocalKeys {
    private MontogramLocalKeys() {}
    private static String alias(MontogramLocalFeature feature, int account, long self) throws GeneralSecurityException {
        if (feature == null || account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || self <= 0) throw new GeneralSecurityException("Invalid retained-data owner");
        return feature.keyAlias(account,self);
    }
    private static KeyStore store() throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Retained-data worker required");
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
    static SecretKey get(MontogramLocalFeature feature, int account, long self, boolean createForExplicitRetention) throws Exception {
        String alias = alias(feature, account, self);
        KeyStore store = store();
        if (store.containsAlias(alias)) {
            Key key = store.getKey(alias, null);
            if (!(key instanceof SecretKey)) throw new GeneralSecurityException("Retained-data key unavailable");
            return (SecretKey) key;
        }
        if (!createForExplicitRetention) throw new GeneralSecurityException("Retained-data key missing");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
    /** Explicit user reset only; never called by logout or failed decryption. */
    static void remove(MontogramLocalFeature feature, int account, long self) throws Exception { store().deleteEntry(alias(feature, account, self)); }
}
