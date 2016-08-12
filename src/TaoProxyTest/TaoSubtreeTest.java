package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.Path;
import TaoProxy.Subtree;
import TaoProxy.TaoPath;
import TaoProxy.TaoProxy;
import TaoProxy.TaoBlock;
import TaoProxy.TaoBucket;
import TaoProxy.TaoSubtree;
import TaoProxy.Bucket;
import TaoProxy.Block;
import TaoProxy.Constants;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class TaoSubtreeTest {
    @Test
    public void testModify() {
        long systemSize = 246420;
        TaoConfigs.initConfiguration(systemSize);

        Subtree testSubtree = new TaoSubtree();

        // Create empty path
        long pathID = 9;
        Path testPath = new TaoPath(pathID);

        // Create empty buckets
        Bucket[] testBuckets = new Bucket[TaoConfigs.TREE_HEIGHT + 1];

        // Fill in each bucket
        for (int i = 0; i < testBuckets.length; i++) {
            // Create blocks for bucket
            Block[] testBlocks = new Block[Constants.BUCKET_SIZE];
            byte[] bytes = new byte[Constants.BLOCK_SIZE];

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

        testSubtree.addPath(testPath);

        Path p1 = testSubtree.getPathToFlush(pathID);
        Bucket[] newBuckets = p1.getBuckets();

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