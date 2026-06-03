package org.envel.betterportals.shared.net.encryption;

import com.google.inject.Singleton;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Utility to creating a cipher based on a {@link UUID}. This is convenient for configuration, although it's not great practise.
 * This is symmetric encryption.
 */
@Singleton
public class CipherManager {
    private static final int AES_KEY_SIZE = 256; // Bits
    public static final int GCM_NONCE_LENGTH = 12; // Bytes
    private static final int GCM_TAG_LENGTH = 16; // Bytes

    private SecretKey secretKey;
    private SecureRandom random;

    /**
     * Initialises the secret key based on <code>key</code>.
     * @param key The key to base the encryption key on.
     * @throws NoSuchAlgorithmException If the encryption algorithm wasn't found - this shouldn't happen in practise
     */
    public void init(UUID key) throws NoSuchAlgorithmException  {
        random = SecureRandom.getInstance("SHA1PRNG");

        // Create our IV from random bytes with the correct block size
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        random.nextBytes(nonce);

        // Generate a new 256 bit AES key from our UUID
        SecureRandom keyRandom = SecureRandom.getInstance("SHA1PRNG");
        keyRandom.setSeed(uuidToBytes(key));
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_SIZE, keyRandom);
        secretKey = keyGenerator.generateKey();
    }

    private byte[] uuidToBytes(UUID id)    {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(id.getMostSignificantBits());
        buffer.putLong(id.getLeastSignificantBits());
        return buffer.array();
    }

    private byte[] generateRandomNonce() {
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        random.nextBytes(nonce);
        return nonce;
    }

    private GCMParameterSpec getGcmParameterSpec(byte[] nonce) {
        return new GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce);
    }

    public Cipher createEncrypt() throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, getGcmParameterSpec(generateRandomNonce()));
        return cipher;
    }

    public Cipher createDecrypt(byte[] nonce) throws GeneralSecurityException  {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, getGcmParameterSpec(nonce));

        return cipher;
    }
}
