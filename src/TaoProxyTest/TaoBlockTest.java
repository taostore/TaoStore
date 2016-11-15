package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.TaoBlock;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.*;

/**
 * Created by ajmagat on 8/3/16.
 */
public class TaoBlockTest {
    @Test
    public void testSerialize() {
        long systemSize = 246420;
        TaoConfigs.initConfiguration(systemSize);

        TaoBlock b = new TaoBlock();
        b.setBlockID(11);

        byte[] bytes = new byte[TaoConfigs.BLOCK_SIZE];
        Arrays.fill(bytes, (byte) 3);
        b.setData(bytes);

        byte[] serialized = b.serialize();

        TaoBlock fromSerialized = new TaoBlock();
        fromSerialized.initFromSerialized(serialized);

        assertEquals(b.getBlockID(), fromSerialized.getBlockID());
        assertTrue(Arrays.equals(b.getData(), fromSerialized.getData()));
    }
}