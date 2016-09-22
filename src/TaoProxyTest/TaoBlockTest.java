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

        InetSocketAddress one = new InetSocketAddress("localhost", 12345);
        InetSocketAddress two = new InetSocketAddress("localhost", 12345);
        InetSocketAddress three = new InetSocketAddress("localhost", 12346);

        Map<InetSocketAddress, Long> map = new HashMap<>();

        map.put(one, 1L);

        System.out.println("value is " + map.get(two));

        Map<Long, List<Long>> writebackMap = new HashMap<>();

        Long l = 4L;

        List<Long> q = writebackMap.get(l);

        if (q == null) {
            System.out.println("twas null");
            q = new ArrayList<>();
            writebackMap.put(l, q);
        }

        q.add(6L);

        List<Long> w = writebackMap.get(l);

        System.out.println("First element in w is " + w.get(0));

        w.add(9L);

        System.out.println("second element in q is " + q.get(1));
    }
}