package TaoProxyTest;

import TaoProxy.Block;
import TaoProxy.Constants;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class BlockTest {
    @Test
    public void testSerialize() {

//        try {
//            // Create a block
//            long blockID = 10;
//            Block testBlock = new Block(blockID);
//            byte[] bytes = new byte[Constants.BLOCK_SIZE];
//            Arrays.fill( bytes, (byte) 3 );
//            testBlock.setData(bytes);
//
//            // Serialize block
//            byte[] serializedBlock = testBlock.serialize();
//            System.out.println("size of block is " + serializedBlock.length);
//            // Deserialize block
//            Block deserializedBlock = new Block(serializedBlock);
//
//            // Check to see if deserialized block is the same as original block
//            assertEquals(blockID, deserializedBlock.getBlockID());
//            byte[] newBytes = deserializedBlock.getData();
//            assertTrue(Arrays.equals(bytes, newBytes));
//        } catch (Exception e) {
//            fail("Failed: " + e.toString());
//        }
    }
}