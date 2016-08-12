package TaoProxyTest;

import Configuration.TaoConfigs;
import TaoProxy.*;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class TaoProcessorTest {
    @Test
    public void flush() throws Exception {
//        TaoProcessor processor = new TaoProcessor();
//
//        processor.flush(0);
    }

    @Test
    public void testReadPath() {
//        // Run server
//        Runnable r = () -> {
//            TestServer server = new TestServer();
//            server.run();
//        };
//        new Thread(r).start();
//
//        // Set tree height
//        TaoConfigs.TREE_HEIGHT = 4;
//
//        // Create proxy
//        TestProxy proxy = new TestProxy();
//        Processor processor = proxy.getProcessor();
//
//        // Create ClientRequest
//        long blockID = 573;
//        long requestID = 47;
//        InetSocketAddress address = new InetSocketAddress("localhost", 3760);
//
//        ClientRequest readRequest = new ClientRequest(blockID, ClientRequest.READ, requestID, address);
//
//        // Read path
//        processor.readPath(readRequest);
//        proxy.waitOnProxy();
//        assertTrue(proxy.getResponseReceived());
//
//        Path returnedPath = proxy.getReceivedPath();
//
//        int i = 0;
//        int j;
//        for (Bucket bkt : returnedPath.getBuckets()) {
//            j = 0;
//            for (Block blk : bkt.getBlocks()) {
//                int expectedBlockID = Integer.parseInt(Integer.toString(i) + Integer.toString(j));
//                assertEquals(expectedBlockID, blk.getBlockID());
//                j++;
//            }
//            i++;
//        }
    }

    @Test
    public void testAnswerRequest() {

    }
}