package TaoServerTest;

import TaoProxy.Path;
import TaoProxy.TaoProxy;
import TaoProxy.Bucket;
import TaoProxy.Block;
import TaoProxy.Constants;

import TaoServer.TaoServer;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class TaoServerTest {
    @Test
    public void testReadWritePath() {
        // Create server
        TaoServer server = new TaoServer(246420);

        // Set tree height
        TaoProxy.TREE_HEIGHT = server.getHeight();

        // Create empty path
        long pathID = 2;
        Path testPath = new Path(pathID);

        // Create empty buckets
        Bucket[] testBuckets = new Bucket[TaoProxy.TREE_HEIGHT + 1];

        // Fill in each bucket
        for (int i = 0; i < testBuckets.length; i++) {
            // Create blocks for bucket
            Block[] testBlocks = new Block[Constants.BUCKET_SIZE];
            byte[] bytes = new byte[Constants.BLOCK_SIZE];

            testBuckets[i] = new Bucket();

            for (int j = 0; j < testBlocks.length; j++) {
                int blockID = Integer.parseInt(Integer.toString(i) + Integer.toString(j));
                testBlocks[j] = new Block(blockID);
                Arrays.fill(bytes, (byte) blockID);
                testBlocks[j].setData(bytes);

                testBuckets[i].addBlock(testBlocks[j], 1);
            }

            testPath.addBucket(testBuckets[i]);
        }

        // Serialize path
        byte[] serializedPath = testPath.serialize();

        // Write path to server
        server.writePath(pathID, serializedPath);

        // Read path back from server
        byte[] read = server.readPath(pathID);

        // Deserialize path
        Path readPath = new Path(pathID, read);

        // Check to see if deserialized path is the same as original path
        Bucket[] newBuckets = readPath.getBuckets();
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