package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.Block;
import TaoProxy.TaoBlock;
import TaoProxy.TaoBucket;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Created by ajmagat on 8/3/16.
 */
public class TaoBucketTest {
    @Test
    public void testSerialize() {
        long systemSize = 246420;
        TaoConfigs.initConfiguration(systemSize);

        // Create empty bucket
        TaoBucket testBucket = new TaoBucket();

        // Create blocks
        Block[] testBlocks = new TaoBlock[TaoConfigs.BLOCKS_IN_BUCKET];

        byte[] bytes = new byte[TaoConfigs.BLOCK_SIZE];
        for (int i = 0; i < testBlocks.length; i++) {
            testBlocks[i] = new TaoBlock(i);
            Arrays.fill( bytes, (byte) i );
            testBlocks[i].setData(bytes);
            testBucket.addBlock(testBlocks[i], 1);
        }

        // Serialize bucket
        byte[] serializedBucket = testBucket.serialize();

        // Deserialize bucket
        TaoBucket b = new TaoBucket();
        b.initFromSerialized(serializedBucket);

        // Check to see if deserialized bucket is the same as original bucket
        Block[] newBlocks = b.getBlocks();
        for (int i = 0; i < newBlocks.length; i++) {
            // Check the IDs of each block
            assertEquals(testBlocks[i].getBlockID(), newBlocks[i].getBlockID());

            // Check the data of each block
            assertTrue(Arrays.equals(testBlocks[i].getData(), newBlocks[i].getData()));
        }
    }
}