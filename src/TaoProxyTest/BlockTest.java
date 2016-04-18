package TaoProxyTest;

import TaoProxy.Block;
import TaoProxy.Constants;
import org.junit.Test;

import java.io.*;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class BlockTest {
    @Test
    public void testSerialize() {

        try {
            // Create a block
            long blockID = 10;
            Block testBlock = new Block(blockID);
            byte[] bytes = new byte[Constants.BLOCK_SIZE];
            Arrays.fill( bytes, (byte) 3 );
            testBlock.setData(bytes);

            // Serialize block
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = new ObjectOutputStream(bos);
            out.writeObject(testBlock);
            byte[] yourBytes = bos.toByteArray();

            // Deserialize block
            ByteArrayInputStream bis = new ByteArrayInputStream(yourBytes);
            ObjectInput in = new ObjectInputStream(bis);
            Block b = (Block) in.readObject();

            // Check to see if deserialized block is the same as original block
            assertEquals(blockID, b.getBlockID());
            byte[] newBytes = b.getData();
            for (int i = 0; i < newBytes.length; i++) {
                assertEquals(bytes[i], newBytes[i]);
            }

            // Close streams
            bos.close();
            out.close();
            bis.close();
            in.close();
        } catch (Exception e) {
            fail("Failed: " + e.toString());
        }
    }
}