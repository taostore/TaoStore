package TaoProxy;

import TaoServer.ServerUtility;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * @brief Class that represents the proxy which handles requests from clients and replies from servers
 */
public class TaoProxy implements Proxy {
    // Sequencer for proxy
    private Sequencer mSequencer;

    // Processor for proxy
    private Processor mProcessor;

    private AsynchronousChannelGroup mThreadGroup;

    // The height of the ORAM tree stored on the server
    public static int TREE_HEIGHT;

    // The total amount of storage being outsourced to server
    public static long TOTAL_STORAGE_SIZE;

    /**
     * @brief Default constructor
     */
    public TaoProxy(long minServerSize) {
        // Create a thread pool for asynchronous sockets
        try {
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(Constants.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());
            mSequencer = new TaoSequencer();
            mProcessor = new TaoProcessor(this, mThreadGroup);

            // Calculate the size of the ORAM tree in both height and total storage
            TREE_HEIGHT = ServerUtility.calculateHeight(minServerSize);
            TOTAL_STORAGE_SIZE = ServerUtility.calculateSize(TREE_HEIGHT);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initializeServer() {
        try {
            int totalPaths = 1 << TaoProxy.TREE_HEIGHT;
            System.out.println("Total paths " + totalPaths);
            byte[] dataToWrite = null;
            int pathSize = 0;

            for (int i = 0; i < totalPaths; i++) {
                System.out.println("Creating path " + i);
                Socket socket = new Socket("localhost", 12345);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                InputStream input = socket.getInputStream();

                Path defaultPath = new Path(i);
//                if (dataToWrite == null) {
                    dataToWrite = defaultPath.serialize();
                    pathSize = dataToWrite.length;
  //              } else {
    //                dataToWrite = Bytes.concat(dataToWrite, defaultPath.serialize());
      //          }


                ProxyRequest writebackRequest = new ProxyRequest(ProxyRequest.WRITE, pathSize, dataToWrite);

                byte[] encryptedWriteBackPaths = writebackRequest.serialize();
                byte[] messageTypeBytes = Ints.toByteArray(Constants.PROXY_WRITE_REQUEST);
                byte[] messageLengthBytes = Ints.toByteArray(encryptedWriteBackPaths.length);
                output.write(Bytes.concat(messageTypeBytes, messageLengthBytes));
                output.write(encryptedWriteBackPaths);

                byte[] typeAndSize = new byte[8];
                input.read(typeAndSize);

                int type = Ints.fromByteArray(Arrays.copyOfRange(typeAndSize, 0, 4));
                int length = Ints.fromByteArray(Arrays.copyOfRange(typeAndSize, 4, 8));

                byte[] message = new byte[length];
                input.read(message);

                output.close();
                input.close();
                socket.close();
            }
            run();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReceiveRequest(ClientRequest req) {
        // When we receive a request, we first send it to the sequencer
        mSequencer.onReceiveRequest(req);

        // We then send the request to the processor, starting with the read path method
        mProcessor.readPath(req);
    }

    @Override
    public void onReceiveResponse(ClientRequest req, ServerResponse resp, boolean isFakeRead) {
        // When a response is received, the processor will answer the request, flush the path, then may perform a
        // write back
        mProcessor.answerRequest(req, resp, isFakeRead);
        mProcessor.flush(resp.getPathID());
        mProcessor.writeBack(Constants.WRITE_BACK_THRESHOLD);
    }

    @Override
    public void notifySequencer(ClientRequest req, ServerResponse resp, byte[] data) {
        // TODO: Find a way to not need this
        mSequencer.onReceiveResponse(req, resp, data);
    }

    public void run() {
        try {
            System.out.println("going to run the proxy");
            // Create an asynchronous channel to listen for connections
            AsynchronousServerSocketChannel channel =
                    AsynchronousServerSocketChannel.open(mThreadGroup).bind(new InetSocketAddress(12344));

            // Asynchronously wait for incoming connections
            channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                @Override
                public void completed(AsynchronousSocketChannel ch, Void att) {
                    // Start listening for other connections
                    channel.accept(null, this);

                    // Create a ByteBuffer to read in message type
                    ByteBuffer typeByteBuffer = ByteBuffer.allocate(4);

                    // Asynchronously read message
                    ch.read(typeByteBuffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            // Flip the byte buffer for reading
                            typeByteBuffer.flip();

                            // Figure out the type of the message
                            byte[] messageTypeBytes = new byte[4];
                            typeByteBuffer.get(messageTypeBytes);

                            // TODO: decryption of messageTypeBytes
                            int messageType = Ints.fromByteArray(messageTypeBytes);

                            // Serve message based on type
                            if (messageType == Constants.CLIENT_REQUEST) {
                                // If dealing with a client request, need to figure out how large the message is
                                ByteBuffer messageSizeByteBuffer = ByteBuffer.allocate(4);

                                // Do another asynchronous read
                                ch.read(messageSizeByteBuffer, null, new CompletionHandler<Integer, Void>() {
                                    @Override
                                    public void completed(Integer result, Void attachment) {
                                        // Flip the byte buffer for reading
                                        messageSizeByteBuffer.flip();

                                        // Figure out the size of the rest of the message
                                        byte[] messageSizeBytes = new byte[4];
                                        messageSizeByteBuffer.get(messageSizeBytes);

                                        // TODO: decryption of messageSizeBytes
                                        int messageSize = Ints.fromByteArray(messageSizeBytes);

                                        // Get the rest of the message
                                        ByteBuffer messageByteBuffer = ByteBuffer.allocate(messageSize);

                                        // Do one last asynchronous read to get the rest of the message
                                        ch.read(messageByteBuffer, null, new CompletionHandler<Integer, Void>() {
                                            @Override
                                            public void completed(Integer result, Void attachment) {
                                                // Flip the byte buffer for reading
                                                messageByteBuffer.flip();

                                                // Get the rest of the bytes for the message
                                                byte[] requestBytes = new byte[messageSize];
                                                messageByteBuffer.get(requestBytes);

                                                // TODO: decryption of requestBytes
                                                // Create ClientRequest object based on read bytes
                                                ClientRequest clientReq = new ClientRequest(requestBytes);

                                                // Handle request
                                                onReceiveRequest(clientReq);
                                            }

                                            @Override
                                            public void failed(Throwable exc, Void attachment) {
                                                // TODO: implement?
                                            }
                                        });
                                    }
                                    @Override
                                    public void failed(Throwable exc, Void attachment) {
                                        // TODO: implement?
                                    }
                                });
                            }
                        }
                        @Override
                        public void failed(Throwable exc, Void attachment) {
                            // TODO: implement?
                        }
                    });
                }
                @Override
                public void failed(Throwable exc, Void att) {
                    // TODO: implement?
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Create proxy and run
        TaoProxy proxy = new TaoProxy(Long.parseLong(args[0]));
        proxy.run();
    }
}
