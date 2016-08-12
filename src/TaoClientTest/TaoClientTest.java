package TaoClientTest;

import Configuration.TaoConfigs;
import Messages.MessageCreator;
import TaoClient.TaoClient;
import TaoProxy.TaoProxy;
import TaoProxy.PathCreator;
import TaoProxy.*;
import TaoServer.TaoServer;
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
            MessageCreator m = new TaoMessageCreator();
            TaoServer server = new TaoServer(systemSize, m);

            // Run server
            server.run();
        };
        new Thread(serverRunnable).start();

        Runnable proxyRunnable = () -> {
            // Create proxy
            MessageCreator n = new TaoMessageCreator();
            PathCreator p = new TaoBlockCreator();
            Subtree s = new TaoSubtree();
            TaoProxy proxy = new TaoProxy(systemSize, n, p, s);

            proxy.initializeServer();
        };
        new Thread(proxyRunnable).start();

        // Wait 1 second for the server and proxy to come up
        try {
            Thread.sleep(5000);
        } catch (Exception e) {
            e.printStackTrace();
        }

       // System.out.println("done sleeping");
        TaoClient client = new TaoClient();

        // Send write request
        long blockID = 3;
        byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
        Arrays.fill(dataToWrite, (byte) blockID);
        TaoLogger.log("@@@@@@@@@@@@ Going to send write request for " + blockID);
        boolean writeStatus = client.write(blockID, dataToWrite);
        assertTrue(writeStatus);

//        try {
//            Thread.sleep(5000);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        TaoLogger.log("\n\n");

        // Send write request
        blockID = 6;
        byte[] dataToWrite1 = new byte[TaoConfigs.BLOCK_SIZE];
        Arrays.fill(dataToWrite1, (byte) blockID);
        TaoLogger.log("@@@@@@@@@@@@ Going to send write request for " + blockID);
        boolean writeStatus1 = client.write(blockID, dataToWrite1);
        assertTrue(writeStatus1);

        TaoLogger.log("\n\n");


        blockID = 3;
        TaoLogger.log("@@@@@@@@@@@@ Going to send read request for " + blockID);
        byte[] s = client.read(blockID);

        TaoLogger.log("Read request for blockID " + blockID + " has data:");

//        try {
//            Thread.sleep(3000);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }

        for (byte b : s) {
            TaoLogger.log(b);
        }
        TaoLogger.log("\n\n\n\n");

        TaoLogger.log("\n\n");

        blockID = 6;
        TaoLogger.log("@@@@@@@@@@@@ Going to send read request for " + blockID);
        byte[] y = client.read(blockID);

        TaoLogger.log("Read request for blockID " + blockID + " has data:");


        for (byte b : y) {
            TaoLogger.log(b);
        }
        TaoLogger.log("\n\n\n\n");

        for (int i = 0; i < 1000; i++) {
            if (i % 2 == 0) {
                blockID = 3;
            } else {
                blockID = 6;
            }
            byte[] z = client.read(blockID);

            TaoLogger.log("11 Read request for blockID " + blockID + " has data:");
            for (byte b : z) {
                TaoLogger.log(b);
            }
            TaoLogger.log();

            if (i % 2 == 0) {
                assertTrue(Arrays.equals(dataToWrite, z));
            } else {
                assertTrue(Arrays.equals(dataToWrite1, z));
            }
        }
    }
}