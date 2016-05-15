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

            proxy.initializeServer();
        };
        new Thread(proxyRunnable).start();

        // Wait 1 second for the server and proxy to come up
        try {
            Thread.sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        TaoClient client = new TaoClient();

        // Send write request
        long blockID = 3;
        byte[] dataToWrite = new byte[Constants.BLOCK_SIZE];
        Arrays.fill(dataToWrite, (byte) blockID);
        System.out.println("@@@@@@@@@@@@ Going to send write request for " + blockID);
        boolean writeStatus = client.write(blockID, dataToWrite);
        assertTrue(writeStatus);

//        try {
//            Thread.sleep(5000);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        System.out.println("\n\n");

        // Send write request
        blockID = 6;
        byte[] dataToWrite1 = new byte[Constants.BLOCK_SIZE];
        Arrays.fill(dataToWrite1, (byte) blockID);
        System.out.println("@@@@@@@@@@@@ Going to send write request for " + blockID);
        boolean writeStatus1 = client.write(blockID, dataToWrite1);
        assertTrue(writeStatus1);

        System.out.println("\n\n");


        blockID = 3;
        System.out.println("@@@@@@@@@@@@ Going to send read request for " + blockID);
        byte[] s = client.read(blockID);

        System.out.println("Read request for blockID " + blockID + " has data:");

//        try {
//            Thread.sleep(3000);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        for (byte b : s) {
            System.out.print(b);
        }
        System.out.println("\n\n\n\n");

        System.out.println("\n\n");

        blockID = 6;
        System.out.println("@@@@@@@@@@@@ Going to send read request for " + blockID);
        byte[] y = client.read(blockID);

        System.out.println("Read request for blockID " + blockID + " has data:");


        for (byte b : y) {
            System.out.print(b);
        }
        System.out.println("\n\n\n\n");

        for (int i = 0; i < 1000; i++) {
            if (i % 2 == 0) {
                blockID = 3;
            } else {
                blockID = 6;
            }
            byte[] z = client.read(blockID);

            System.out.println("11 Read request for blockID " + blockID + " has data:");
            for (byte b : z) {
                System.out.print(b);
            }
            System.out.println();

            if (i % 2 == 0) {
                assertTrue(Arrays.equals(dataToWrite, z));
            } else {
                assertTrue(Arrays.equals(dataToWrite1, z));
            }
        }
    }
}