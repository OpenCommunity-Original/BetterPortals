import org.envel.betterportals.shared.net.encryption.CipherManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EncryptionTests {
    private CipherManager cipherManager;
    private byte[] data;

    @BeforeEach
    public void setUp() throws NoSuchAlgorithmException {
        cipherManager = new CipherManager();
        cipherManager.init(UUID.fromString("c27769c6-41f1-4bd7-a0f1-b6fb62b213a7"));

        Random random = new Random(0);
        data = new byte[1024];
        random.nextBytes(data);
    }

    @Test
    public void testEncryptAndDecrypt() throws GeneralSecurityException {
        Cipher cipher = cipherManager.createEncrypt();
        byte[] encrypted = cipher.doFinal(data);
        byte[] decrypted = cipherManager.createDecrypt(cipher.getIV()).doFinal(encrypted);

        for(int i = 0; i < data.length; i++) {
            assertEquals(data[i], decrypted[i]);
        }
    }

    @Test
    public void testInvalidKey() {
        assertThrows(AEADBadTagException.class, () -> {
            CipherManager invalidCipher = new CipherManager();
            invalidCipher.init(UUID.fromString("6f653d86-c69c-4d3c-95b3-38037eedddd7"));

            Cipher cipher = cipherManager.createEncrypt();
            byte[] encrypted = cipher.doFinal(data);
            invalidCipher.createDecrypt(cipher.getIV()).doFinal(encrypted);
        });
    }
}
