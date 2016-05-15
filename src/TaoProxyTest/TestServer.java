package TaoProxyTest;

import TaoProxy.*;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Created by ajmagat on 5/4/16.
 */
public class TestServer {
    public TestServer() {

    }

    public void run() {
 //       try {
//            // Create server socket and wait for connection
//            ServerSocket serverSocket = new ServerSocket(12345);
//            Socket clientServerSocket = serverSocket.accept();
//
//            // Create input stream
//            InputStream input = clientServerSocket.getInputStream();
//
//            // Get the message type
//            byte[] messageType = new byte[4];
//            input.read(messageType, 0, messageType.length);
//            int messageTypeInt = Ints.fromByteArray(messageType);
//
//            // Serve request based on type
//            if (messageTypeInt == Constants.PROXY_READ_REQUEST) {
//                // Read rest of request
//                byte[] message = new byte[ProxyRequest.getProxyReadRequestSize()];
//                input.read(message, 0, message.length);
//
//                // Parse bytes to ProxyRequest
//                ProxyRequest request = new ProxyRequest(message);
//
//                // Create output stream to proxy
//                DataOutputStream output = new DataOutputStream(clientServerSocket.getOutputStream());
//
//                // Create response
//                Path samplePath = getFilledPath(request.getPathID());
//                ServerResponse response = new ServerResponse(samplePath);
//
//                // Send response
//                output.write(response.serializeAsMessage());
//            } else if (messageTypeInt == Constants.PROXY_WRITE_REQUEST) {
//                byte[] message = new byte[ProxyRequest.getProxyWriteRequestSize()];
//                input.read(message, 0, message.length);
//               // byte[] messageBytes = new String(message).getBytes(StandardCharsets.UTF_8);
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }

    public Path getFilledPath(long pathID) {
        Path newPath = new Path(pathID);

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

            newPath.addBucket(testBuckets[i]);
        }

        return newPath;
    }

}
