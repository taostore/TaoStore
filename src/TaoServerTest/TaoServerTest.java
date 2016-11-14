package TaoServerTest;

import Configuration.TaoConfigs;
import TaoProxy.*;

import TaoServer.TaoServer;

import org.junit.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class TaoServerTest {
    @Test
    public void testReadWritePath() {
        TaoConfigs.PARTITION_SERVERS = TaoConfigs.TEST_PARTITION_SERVERS;
        // Create server
        TaoServer server = new TaoServer(246420, new TaoMessageCreator());
        TaoCryptoUtil cryptoUtil = new TaoCryptoUtil();

        // Create empty path
        long pathID = 3;
        TaoPath testPath = new TaoPath(pathID);

        // Create empty buckets
        TaoBucket[] testBuckets = new TaoBucket[TaoConfigs.TREE_HEIGHT + 1];

        // Fill in each bucket
        for (int i = 0; i < testBuckets.length; i++) {
            // Create blocks for bucket
            TaoBlock[] testBlocks = new TaoBlock[TaoConfigs.BLOCKS_IN_BUCKET];
            byte[] bytes = new byte[TaoConfigs.BLOCK_SIZE];

            testBuckets[i] = new TaoBucket();

            for (int j = 0; j < testBlocks.length; j++) {
                int blockID = Integer.parseInt(Integer.toString(i) + Integer.toString(j));
                System.out.println("blockid " + blockID);
                testBlocks[j] = new TaoBlock(blockID);
                Arrays.fill(bytes, (byte) blockID);
                testBlocks[j].setData(bytes);

                testBuckets[i].addBlock(testBlocks[j], j);
            }

            testPath.addBucket(testBuckets[i]);
        }

        // Serialize and encrypt path
        byte[] serializedPath = cryptoUtil.encryptPath(testPath);

        // Remove the id bytes since we are passing this straight into the writePath function
        serializedPath = Arrays.copyOfRange(serializedPath, 8, serializedPath.length);
        // Write path to server
        server.writePath(pathID, serializedPath, 0);

        // Read path back from server
        byte[] read = server.readPath(pathID);

        // Deserialize bucket
        // Path deserializedPath = cryptoUtil.decryptPath(serializedPath);
        Path deserializedPath = cryptoUtil.decryptPath(read);

        //new TaoPath();
        //deserializedPath.initFromSerialized(serializedPath);

        // Check to see if deserialized path is the same as original path
        Bucket[] newBuckets = deserializedPath.getBuckets();
        for (int i = 0; i < newBuckets.length; i++) {
            Block[] newBlocks = newBuckets[i].getBlocks();
            Block[] testBlocks = testBuckets[i].getBlocks();
            for (int j = 0; j < newBlocks.length; j++) {
                // Check the IDs of each block
                assertEquals(testBlocks[j].getBlockID(), newBlocks[j].getBlockID());

                // Check the data of each block
                assertTrue(Arrays.equals(testBlocks[j].getData(), newBlocks[j].getData()));
            }
        }
    }
}