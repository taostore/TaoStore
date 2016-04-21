package TaoProxyTest;

import TaoProxy.Block;
import TaoProxy.Bucket;
import TaoProxy.Constants;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class BucketTest {
    @Test
    public void testSerialize() {
        try {
            // Create empty bucket
            Bucket testBucket = new Bucket();

            // Create blocks
            Block[] testBlocks = new Block[Constants.BUCKET_SIZE];
            byte[] bytes = new byte[Constants.BLOCK_SIZE];
            for (int i = 0; i < testBlocks.length; i++) {
                testBlocks[i] = new Block(i);
                Arrays.fill( bytes, (byte) i );
                testBlocks[i].setData(bytes);
                testBucket.addBlock(testBlocks[i], 1);
            }

            // Serialize bucket
            byte[] serializedBucket = testBucket.serialize();

            System.out.println("size of bucket is " + serializedBucket.length);
            // Deserialize bucket
            Bucket b = new Bucket(serializedBucket);

            // Check to see if deserialized bucket is the same as original bucket
            Block[] newBlocks = b.getBlocks();
            for (int i = 0; i < newBlocks.length; i++) {
                // Check the IDs of each block
                assertEquals(testBlocks[i].getBlockID(), newBlocks[i].getBlockID());

                // Check the data of each block
                assertTrue(Arrays.equals(testBlocks[i].getData(), newBlocks[i].getData()));
            }
        } catch (Exception e) {
            fail("Failed: " + e.toString());
        }
    }
}