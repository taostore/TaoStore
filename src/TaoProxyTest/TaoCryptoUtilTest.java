package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.*;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by ajmagat on 8/2/16.
 */
public class TaoCryptoUtilTest {
    @Test
    public void testEncryptDecryptPath() {
        TaoConfigs.initConfiguration();
        TaoCryptoUtil mCryptoUtil = new TaoCryptoUtil();

        PathCreator pc = new TaoBlockCreator();

        Path p = pc.createPath();
        p.setPathID(4);

        byte[] encryption = mCryptoUtil.encryptPath(p);

        Path unencrypted = mCryptoUtil.decryptPath(encryption);


        Bucket[] oldBuckets = p.getBuckets();
        Bucket[] newBuckets = unencrypted.getBuckets();
        for (int i = 0; i < newBuckets.length; i++) {
            Block[] newBlocks = newBuckets[i].getBlocks();
            Block[] testBlocks = oldBuckets[i].getBlocks();
            for (int j = 0; j < newBlocks.length; j++) {
                // Check the IDs of each block
                assertEquals(testBlocks[j].getBlockID(), newBlocks[j].getBlockID());

                // Check the data of each block
                assertTrue(Arrays.equals(testBlocks[j].getData(), newBlocks[j].getData()));
            }
        }

        assertEquals(p.getPathID(), unencrypted.getPathID());
    }
}