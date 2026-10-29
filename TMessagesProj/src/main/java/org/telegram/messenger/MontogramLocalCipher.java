/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Owner- and feature-bound retained-data envelope only. Key lookup, consent and files belong to the
 * owner-bound store. The default process-memory path must never call this.
 */
final class MontogramLocalCipher {
    private static final int MAGIC = 0x4d4c4431, VERSION = 1;
    private static final int HEADER_BYTES = 18, IV_BYTES = 12, TAG_BYTES = 16;
    static int maxEnvelopeBytes(MontogramLocalFeature feature) { return feature.maxBytes + HEADER_BYTES + IV_BYTES + TAG_BYTES; }

    private MontogramLocalCipher() {}

    private static byte[] header(MontogramLocalFeature feature, int account, long self) throws GeneralSecurityException {
        if (feature == null || account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || self <= 0) {
            throw new GeneralSecurityException("Invalid retained-data owner");
        }
        return ByteBuffer.allocate(HEADER_BYTES).putInt(MAGIC).put((byte) VERSION)
            .put((byte) feature.kind).putInt(account).putLong(self).array();
    }

    static byte[] seal(MontogramLocalFeature feature, SecretKey key, int account, long self, byte[] plaintext) throws GeneralSecurityException {
        byte[] context = header(feature, account, self);
        if (key == null || plaintext == null || plaintext.length > feature.maxBytes) {
            throw new GeneralSecurityException("Invalid retained-data payload");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        // Provider-generated random IV; never derive it from account or revision.
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_BYTES) throw new GeneralSecurityException("Unsupported retained-data IV");
        cipher.updateAAD(context);
        byte[] encrypted = cipher.doFinal(plaintext);
        if (encrypted.length != plaintext.length + TAG_BYTES) throw new GeneralSecurityException("Unsupported retained-data tag");
        return ByteBuffer.allocate(context.length + iv.length + encrypted.length)
            .put(context).put(iv).put(encrypted).array();
    }

    static byte[] open(MontogramLocalFeature feature, SecretKey key, int account, long self, byte[] envelope) throws GeneralSecurityException {
        byte[] expected = header(feature, account, self);
        if (key == null || envelope == null || envelope.length < HEADER_BYTES + IV_BYTES + TAG_BYTES ||
                envelope.length > maxEnvelopeBytes(feature)) {
            throw new GeneralSecurityException("Invalid retained-data envelope");
        }
        byte[] context = Arrays.copyOfRange(envelope, 0, HEADER_BYTES);
        if (!MessageDigest.isEqual(expected, context)) throw new GeneralSecurityException("Retained-data context mismatch");
        byte[] iv = Arrays.copyOfRange(envelope, HEADER_BYTES, HEADER_BYTES + IV_BYTES);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BYTES * 8, iv));
        cipher.updateAAD(context);
        // Authentication completes before any bytes are returned to the codec/UI.
        return cipher.doFinal(envelope, HEADER_BYTES + IV_BYTES, envelope.length - HEADER_BYTES - IV_BYTES);
    }
}
