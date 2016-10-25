package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.Path;
import TaoProxy.PathCreator;
import TaoProxy.TaoBlockCreator;
import TaoProxy.TaoCryptoUtil;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.security.NoSuchAlgorithmException;

import static org.junit.Assert.*;

/**
 * Created by ajmagat on 8/2/16.
 */
public class TaoCryptoUtilTest {
    @Test
    public void testEncryptDecryptPath() {
        long systemSize = 246420;

        TaoConfigs.initConfiguration(systemSize);
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        keyGen.init(128);
        SecretKey mKey = keyGen.generateKey();
        TaoCryptoUtil mCryptoUtil = new TaoCryptoUtil(mKey);
        System.out.println(TaoConfigs.ENCRYPTED_BUCKET_SIZE);
        PathCreator pc = new TaoBlockCreator();

        Path p = pc.createPath();
        p.setPathID(4);

        byte[] encryption = mCryptoUtil.encryptPath(p);

        Path unencrypted = mCryptoUtil.decryptPath(encryption);

        assertEquals(p.getPathID(), unencrypted.getPathID());

    }
}