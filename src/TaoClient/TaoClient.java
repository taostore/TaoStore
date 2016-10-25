package TaoClient;

import Configuration.TaoConfigs;
import Messages.*;
import TaoProxy.*;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertTrue;

/**
 * @brief Class to represent a client of TaoStore
 */
public class TaoClient implements Client {
    // The address of the proxy
    private InetSocketAddress mProxyAddress;

    // The address of this client
    private InetSocketAddress mClientAddress;

    // A MessageCreator to create different types of messages to be passed from client, proxy, and server
    private MessageCreator mMessageCreator;

    // Counter to keep track of current request number
    // Incremented after each request
    private static int mRequestID = 0;

    // Thread group for asynchronous sockets
    private AsynchronousChannelGroup mThreadGroup;

    private Map<Long, ProxyResponse> mResponseWaitMap;

    /**
     * @brief Default constructor
     */
    public TaoClient() {
        try {
            mProxyAddress = new InetSocketAddress(TaoConfigs.PROXY_HOSTNAME, TaoConfigs.PROXY_PORT);
            mClientAddress = new InetSocketAddress(TaoConfigs.CLIENT_HOSTNAME, TaoConfigs.CLIENT_PORT);
            mMessageCreator = new TaoMessageCreator();
            mResponseWaitMap = new ConcurrentHashMap<>();

            // Create a thread pool for asynchronous sockets
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());
            listenForResponse();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Constructor that takes in an address for the proxy
     * @param proxyAddress
     * @param proxyPort
     */
    public TaoClient(String proxyAddress, int proxyPort) {
        mProxyAddress = new InetSocketAddress(proxyAddress, proxyPort);
        mClientAddress = new InetSocketAddress(TaoConfigs.CLIENT_HOSTNAME, TaoConfigs.CLIENT_PORT);
        mMessageCreator = new TaoMessageCreator();
    }

    @Override
    public byte[] read(long blockID) {
        try {
            // Send read request
            ProxyResponse response = sendRequest(MessageTypes.CLIENT_READ_REQUEST, blockID, null);

            // Return read data
            return response.getReturnData();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean write(long blockID, byte[] data) {
        try {
            // Send write request
            ProxyResponse response = sendRequest(MessageTypes.CLIENT_WRITE_REQUEST, blockID, data);
            return response.getWriteStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    private ProxyResponse sendRequest(int type, long blockID, byte[] data) {
        try {
            TaoLogger.logForce("In sendRequest");
            // Create client request
            ClientRequest request = mMessageCreator.createClientRequest();
            long requestID = mRequestID;
            mRequestID++;

            // Create an empty response
            ProxyResponse proxyResponse = mMessageCreator.createProxyResponse();

            mResponseWaitMap.put(requestID, proxyResponse);

            // Set data for request
            request.setBlockID(blockID);
            request.setRequestID(requestID);
            request.setClientAddress(mClientAddress);

            // Set additional data depending on message type
            if (type == MessageTypes.CLIENT_READ_REQUEST) {
                request.setType(MessageTypes.CLIENT_READ_REQUEST);
            } else if (type == MessageTypes.CLIENT_WRITE_REQUEST) {
                request.setType(MessageTypes.CLIENT_WRITE_REQUEST);
                request.setData(data);
            }


            // Wait until response
            synchronized (proxyResponse) {
                sendRequest(request);
                TaoLogger.logForce("Waiting for response");
                proxyResponse.wait();
            }

            return proxyResponse;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * @brief Private helper method to send a read or write request to a TaoStore proxy
     * @param request
     * @return a ProxyResponse
     */
    private void sendRequest(ClientRequest request) {
        try {
            TaoLogger.logForce("Going to open channel");
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
            TaoLogger.logForce("Opened channel");
            channel.connect(mProxyAddress, null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    // Send request to proxy
                    byte[] serializedRequest = request.serialize();
                    byte[] requestHeader = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
                    ByteBuffer requestMessage = ByteBuffer.wrap(Bytes.concat(requestHeader, serializedRequest));
                    channel.write(requestMessage, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            if (requestMessage.remaining() > 0) {
                                TaoLogger.logForce("did not send all the data, still have " + requestMessage.remaining());
                                channel.write(requestMessage, null, this);
                                return;
                            }
                            TaoLogger.logForce("Finished writing");
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {

                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {

                }
            });
//                TaoLogger.logForce("sendRequest function");
//            // Get proxy name and port
//            Socket clientSocket = new Socket(mProxyAddress.getHostName(), mProxyAddress.getPort());
//            TaoLogger.logForce("sendRequest function 0.25");
//            // Create output stream
//            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());
//            TaoLogger.logForce("sendRequest function 0.50");
//            // Create client request
//            ClientRequest request = mMessageCreator.createClientRequest();
//            TaoLogger.logForce("sendRequest function 0.75");
//            // Create request id
//            // TODO: generate random request ID, or just sequentially increase?
//            long requestID = mRequestID;
//            mRequestID++;
//
//            // Create an empty response
//            ProxyResponse proxyResponse = mMessageCreator.createProxyResponse();
//
//            mResponseWaitMap.put(requestID, proxyResponse);
//
//            // Set data for request
//            request.setBlockID(blockID);
//            request.setRequestID(requestID);
//            request.setClientAddress(mClientAddress);
//            TaoLogger.logForce("sendRequest function 1");
//            // Set additional data depending on message type
//            if (type == MessageTypes.CLIENT_READ_REQUEST) {
//                request.setType(MessageTypes.CLIENT_READ_REQUEST);
//            } else if (type == MessageTypes.CLIENT_WRITE_REQUEST) {
//                request.setType(MessageTypes.CLIENT_WRITE_REQUEST);
//                request.setData(data);
//            }
//
//            // Send request to proxy
//            byte[] serializedRequest = request.serialize();
//            byte[] header = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
//            TaoLogger.logForce("Really sent request");
//            output.write(Bytes.concat(header, serializedRequest));
//           // output.write(serializedRequest);
//
//            // Close streams and ports
//            clientSocket.close();
//            output.close();
//
//            // Wait until response
//            synchronized (proxyResponse) {
//                proxyResponse.wait();
//            }
//
//            // Return proxy response
//            return proxyResponse;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // IDEA:
    // Central listenForResponse
    // Has a map of objects: maps requestID to and object
    // When it receives an

    /**
     * @brief Private helper method that will wait for proxy responses
     */
    // TODO: Responses might come out of order, need to handle this
    private void listenForResponse() {
        // Create runnable to listen for a ProxyResponse
        Runnable r = () -> {
            try {
                // Create an asynchronous channel to listen for connections
                AsynchronousServerSocketChannel channel =
                        AsynchronousServerSocketChannel.open(mThreadGroup).bind(new InetSocketAddress(mClientAddress.getPort()));
                // Asynchronously wait for incoming connections
                channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                    @Override
                    public void completed(AsynchronousSocketChannel ch, Void att) {
                        // Start listening for other connections
                        channel.accept(null, this);

                        TaoLogger.log("Just made a connection");
                        // Create a ByteBuffer to read in message type
                        ByteBuffer typeByteBuffer = MessageUtility.createTypeReceiveBuffer();

                        // Asynchronously read message
                        ch.read(typeByteBuffer, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result, Void attachment) {
                                TaoLogger.log("Going to read header");
                                // Flip the byte buffer for reading
                                typeByteBuffer.flip();

                                // Figure out the type of the message
                                int[] typeAndLength = MessageUtility.parseTypeAndLength(typeByteBuffer);
                                int messageType = typeAndLength[0];
                                int messageLength = typeAndLength[1];

                                // Serve message based on type
                                if (messageType == MessageTypes.PROXY_RESPONSE) {
                                    // Get the rest of the message
                                    ByteBuffer messageByteBuffer = ByteBuffer.allocate(messageLength);

                                    TaoLogger.log("Going to read rest of message");
                                    // Do one last asynchronous read to get the rest of the message
                                    ch.read(messageByteBuffer, null, new CompletionHandler<Integer, Void>() {
                                        @Override
                                        public void completed(Integer result, Void attachment) {
                                            TaoLogger.log("Read at least some of message");
                                            // Make sure we read all the bytes
                                            while (messageByteBuffer.remaining() > 0) {
                                                TaoLogger.log("Going to read more of message");
                                                ch.read(messageByteBuffer, null, this);
                                                return;
                                            }
                                            TaoLogger.log("Read all of message");
                                            // Flip the byte buffer for reading
                                            messageByteBuffer.flip();

                                            // Get the rest of the bytes for the message
                                            byte[] requestBytes = new byte[messageLength];
                                            messageByteBuffer.get(requestBytes);

                                            // Initialize ProxyResponse object based on read bytes
                                            ProxyResponse proxyResponse = mMessageCreator.createProxyResponse();
                                            proxyResponse.initFromSerialized(requestBytes);

                                            // Notify thread waiting for this response id
                                            ProxyResponse clientAnswer = mResponseWaitMap.get(proxyResponse.getClientRequestID());
                                            clientAnswer.initFromSerialized(requestBytes);
                                            synchronized (clientAnswer) {
                                                clientAnswer.notifyAll();
                                                mResponseWaitMap.remove(proxyResponse.getClientRequestID());
                                            }
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
        };

        // Start runnable on new thread
        new Thread(r).start();
    }

    @Override
    public void printSubtree() {
        try {
            // Get proxy name and port
            Socket clientSocket = new Socket(mProxyAddress.getHostName(), mProxyAddress.getPort());

            // Create output stream
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // Create client request
            ClientRequest request = mMessageCreator.createClientRequest();
            request.setType(MessageTypes.PRINT_SUBTREE);

            byte[] serializedRequest = request.serialize();
            byte[] header = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
            output.write(header);

            // Close streams and ports
            clientSocket.close();
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadTest(Client client) {
        long blockID = 3;
        TaoLogger.logForce("Starting load test");
        byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
        Arrays.fill(dataToWrite, (byte) blockID);
        TaoLogger.log("@@@@@@@@@@@@ Going to send write request for " + blockID);
        boolean writeStatus = client.write(blockID, dataToWrite);
        if (!writeStatus) {
            TaoLogger.log("Exit 1");
            System.exit(1);
        }

        blockID = 6;
        byte[] dataToWrite1 = new byte[TaoConfigs.BLOCK_SIZE];
        Arrays.fill(dataToWrite1, (byte) blockID);
        TaoLogger.log("@@@@@@@@@@@@ Going to send write request for " + blockID);
        boolean writeStatus1 = client.write(blockID, dataToWrite1);
        if (!writeStatus1) {
            TaoLogger.log("Exit 2");
            System.exit(1);
        }

     //   client.printSubtree();

        for (int i = 0; i < 1000; i++) {
            if (i % 2 == 0) {
                blockID = 3;
            } else {
                blockID = 6;
            }

            byte[] z = client.read(blockID);

            TaoLogger.log("k Checking read " + i);

            if (i % 2 == 0) {
                if (!Arrays.equals(dataToWrite, z)) {
                    TaoLogger.log("Exit 3");
                    System.exit(1);
                }

            } else {
                if (!Arrays.equals(dataToWrite1, z)) {
                    TaoLogger.log("Exit 4");
                    System.exit(1);
                }
            }
//            client.printSubtree();
        }
        TaoLogger.logForce("Ending load test");
    }
    public static void main(String[] args) {
        TaoLogger.logOn = false;
        long systemSize = 246420;
        TaoClient client = new TaoClient();
        TaoLogger.log("Going to start load test");
      //  loadTest(client);
        Scanner reader = new Scanner(System.in);
        while (true) {
            TaoLogger.logForce("W for write, R for read, P for print, Q for quit");
            String option = reader.nextLine();

            if (option.equals("Q")) {
                break;
            } else if (option.equals("W")) {
                TaoLogger.logForce("Enter block ID to write to");
                long blockID = reader.nextLong();

                TaoLogger.logForce("Enter number to fill in block");
                long fill = reader.nextLong();
                byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
                Arrays.fill(dataToWrite, (byte) fill);

                TaoLogger.logForce("Going to send write request for " + blockID);
                boolean writeStatus = client.write(blockID, dataToWrite);

                if (writeStatus) {
                    TaoLogger.logForce("Write succeeded");
                } else {
                    TaoLogger.logForce("Write did not succeed");
                    System.exit(1);
                }
            } else if (option.equals("R")) {
                TaoLogger.logForce("Enter block ID to read from");

                long blockID = reader.nextLong();

                TaoLogger.logForce("Going to send read request for " + blockID);
                byte[] result = client.read(blockID);

                TaoLogger.logForce("The result of the read is a block filled with the number " + result[0]);
            } else if (option.equals("P")) {
                client.printSubtree();
            }
        }

        System.exit(1);
    }
}
