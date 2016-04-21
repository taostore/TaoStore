package TaoProxyTest;

import TaoProxy.Path;
import TaoProxy.Subtree;
import TaoProxy.TaoProxy;
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
        Subtree testSubtree = new TaoSubtree();

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

        testSubtree.addPath(testPath);


        Path p1 = testSubtree.getPathToFlush(pathID);
        Bucket[] newBuckets = p1.getBuckets();

        for (int i = 0; i < newBuckets.length; i++) {
            // Create blocks for bucket
            Block[] testBlocks = new Block[Constants.BUCKET_SIZE];
            byte[] bytes = new byte[Constants.BLOCK_SIZE];

            for (int j = 0; j < testBlocks.length; j++) {
                int blockID = Integer.parseInt(Integer.toString(j) + Integer.toString(i));
                testBlocks[j] = new Block(blockID);
                Arrays.fill(bytes, (byte) blockID);
                testBlocks[j].setData(bytes);

                newBuckets[i].addBlock(testBlocks[j], 1);
            }
        }



        Bucket[] newBuckets1 = testSubtree.getPath(pathID).getBuckets();

        for (Bucket b : newBuckets1) {
            for (Block b1 : b.getBlocks()) {
                System.out.println("New block " + b1.getBlockID());
            }
        }

    }
}