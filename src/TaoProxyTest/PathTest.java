package TaoProxyTest;

import TaoProxy.Block;
import TaoProxy.Bucket;
import TaoProxy.Constants;
import TaoProxy.TaoProxy;
import TaoProxy.Path;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class PathTest {
    @Test
    public void testModify() {

    }

    @Test
    public void testSerialize() {
        try {
            // Set tree height
            TaoProxy.TREE_HEIGHT = 4;

            // Create empty path
            long pathID = 9;
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

            // Deserialize bucket
            Path deserializedPath = new Path(serializedPath);

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
        } catch (Exception e) {
            fail("Failed: " + e.toString());
        }
    }
}