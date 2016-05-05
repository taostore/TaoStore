package TaoClientTest;

import TaoClient.TaoClient;
import TaoProxy.Constants;
import TaoProxy.TaoProxy;
import TaoProxyTest.TestServer;
import TaoServer.TaoServer;
import com.google.common.primitives.Bytes;
import org.junit.Test;

import java.util.Arrays;
import static org.junit.Assert.*;

/**
 * Created by ajmagat on 5/3/16.
 */
public class TaoClientTest {
    @Test
    public void testReadWrite() {
        // Set system size
        long systemSize = 246420;

        // Create and run server
        Runnable serverRunnable = () -> {
            // Create server
            TaoServer server = new TaoServer(systemSize);

            // Run server
            server.run();
        };
        new Thread(serverRunnable).start();

        Runnable proxyRunnable = () -> {
            // Create proxy
            TaoProxy proxy = new TaoProxy(systemSize);

            // Run proxy
            proxy.run();
        };
        new Thread(proxyRunnable).start();

        TaoClient client = new TaoClient();

        // Send write request
        long blockID = 3;
        byte[] dataToWrite = new byte[Constants.BLOCK_SIZE];
        Arrays.fill(dataToWrite, (byte) blockID);
        try {
            Thread.sleep(1000);
        } catch (Exception e) {
            e.printStackTrace();
        }
        boolean writeStatus = client.write(blockID, dataToWrite);
        System.out.println("????");
        assertTrue(writeStatus);

        byte[] s = client.read(blockID);

        System.out.println("About to print message");
        for (byte b : s) {
            System.out.print(b);
        }
        System.out.println();

        // Create a read request
    }
}