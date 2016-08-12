package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.*;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 *
 */
public class TaoPathTest {
    @Test
    public void testSerialize() {
        long systemSize = 246420;
        TaoConfigs.initConfiguration(systemSize);

        // Create empty path
        long pathID = 9;
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
                testBlocks[j] = new TaoBlock(blockID);
                Arrays.fill(bytes, (byte) blockID);
                testBlocks[j].setData(bytes);

                testBuckets[i].addBlock(testBlocks[j], 1);
            }

            testPath.addBucket(testBuckets[i]);
        }

        // Serialize path
        byte[] serializedPath = testPath.serialize();

        // Deserialize bucket
        TaoPath deserializedPath = new TaoPath();
        deserializedPath.initFromSerialized(serializedPath);

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






        // Set tree height
//            TaoConfigs.TREE_HEIGHT = 4;
//
//            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
//            keyGen.init(128); // for example
//            SecretKey secretKey = keyGen.generateKey();
//            SecretKey mKey = secretKey;
//            Utility.mSecretKey = mKey;
//
//
//            Bucket testBucket = new Bucket();
//            // Create blocks
//            Block[] testBlocks = new Block[Constants.BUCKET_SIZE];
//            byte[] bytes = new byte[Constants.BLOCK_SIZE];
//            for (int i = 0; i < testBlocks.length; i++) {
//                testBlocks[i] = new Block(i);
//                Arrays.fill( bytes, (byte) i );
//                testBlocks[i].setData(bytes);
//                testBucket.addBlock(testBlocks[i], 1);
//            }
//
//            Path defaultPath = new Path(3);
//            defaultPath.addBucket(testBucket);
//            byte[] pathBytes = defaultPath.serialize();
//
//            long pathID = Longs.fromByteArray(Arrays.copyOfRange(pathBytes, 0, 8));
//            byte[] encryptedBuckets = Arrays.copyOfRange(pathBytes, 8, pathBytes.length);
//            System.out.println(pathID);
//            Path newPath = new Path(pathID, encryptedBuckets);
//
//            Bucket newBucket = newPath.getBucket(0);
//
//            Block[] newBlocks = newBucket.getBlocks();
//            for (int i = 0; i < newBlocks.length; i++) {
//                // Check the IDs of each block
//                assertEquals(testBlocks[i].getBlockID(), newBlocks[i].getBlockID());
//
//                // Check the data of each block
//                assertTrue(Arrays.equals(testBlocks[i].getData(), newBlocks[i].getData()));
//            }

    }
}