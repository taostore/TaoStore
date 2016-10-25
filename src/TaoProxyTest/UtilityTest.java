package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.Utility;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Created by ajmagat on 5/14/16.
 */
public class UtilityTest {
    @Test
    public void testSerialize() {
//        try {
//            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
//            keyGen.init(128); // for example
//            SecretKey secretKey = keyGen.generateKey();
//            SecretKey mKey = secretKey;
//            Utility.mSecretKey = mKey;
//
//            String plaintext = "Hello World!";
//            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
//            System.out.println("The plaintext bytes are: length " + plaintext.length());
//            for (byte b : plaintextBytes) {
//                System.out.print(b);
//            }
//            System.out.println("\n");
//            byte[] encryptedBytes = Utility.encrypt(plaintextBytes);
//
//            System.out.println("The encrypted bytes are: length " + encryptedBytes.length);
//            for (byte b : encryptedBytes) {
//                System.out.print(b);
//            }
//            System.out.println("\n");
//
//
//            byte[] decryptedBytes = Utility.decrypt(encryptedBytes);
//
//            System.out.println("The decrypted bytes are: length " + decryptedBytes.length);
//            for (byte b : decryptedBytes) {
//                System.out.print(b);
//            }
//            System.out.println("\n");
//            String newPlaintext = new String(decryptedBytes, StandardCharsets.UTF_8);
//
//            assertEquals(plaintext, newPlaintext);
//            System.out.println(newPlaintext);
//
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    @Test
    public void testGreatestCommonLevel() {
        long systemSize = 246420;
        TaoConfigs.initConfiguration(systemSize);

        System.out.println(Utility.getGreatestCommonLevel(10, 10));
    }
}