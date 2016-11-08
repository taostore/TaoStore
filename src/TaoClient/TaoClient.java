package TaoClient;

import Configuration.TaoConfigs;
import Messages.*;
import TaoProxy.*;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.assertTrue;

/**
 * @brief Class to represent a client of TaoStore
 */
public class TaoClient implements Client {
    // The address of the proxy
    protected InetSocketAddress mProxyAddress;

    // The address of this client
    protected InetSocketAddress mClientAddress;

    // A MessageCreator to create different types of messages to be passed from client, proxy, and server
    protected MessageCreator mMessageCreator;

    // Counter to keep track of current request number
    // Incremented after each request
    protected static int mRequestID = 0;

    // Thread group for asynchronous sockets
    protected AsynchronousChannelGroup mThreadGroup;

    protected Map<Long, ProxyResponse> mResponseWaitMap;

    protected AsynchronousSocketChannel mChannel;

    protected ExecutorService mExecutor;

    /**
     * @brief Default constructor
     */
    public TaoClient() {
        try {
            System.out.println(InetAddress.getLocalHost().getHostAddress());
            String currentIP = InetAddress.getLocalHost().getHostAddress();
            mProxyAddress = new InetSocketAddress(TaoConfigs.PROXY_HOSTNAME, TaoConfigs.PROXY_PORT);
            mClientAddress = new InetSocketAddress(currentIP, TaoConfigs.CLIENT_PORT);
            // mProxyAddress = new InetSocketAddress("127.0.0.1", TaoConfigs.PROXY_PORT);
            // mClientAddress = new InetSocketAddress("127.0.0.1", TaoConfigs.CLIENT_PORT);
            mMessageCreator = new TaoMessageCreator();
            mResponseWaitMap = new ConcurrentHashMap<>();

            // Create a thread pool for asynchronous sockets
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());
            mChannel = AsynchronousSocketChannel.open(mThreadGroup);
            Future connection = mChannel.connect(mProxyAddress);
            connection.get();

            //
            mExecutor = Executors.newFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());


            Object listenerWait = new Object();
            synchronized (listenerWait) {
                listenForResponse(listenerWait);
                // this is used for listenForResponse set up
                listenerWait.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
                request.setData(new byte[TaoConfigs.BLOCK_SIZE]);
            } else if (type == MessageTypes.CLIENT_WRITE_REQUEST) {
                request.setType(MessageTypes.CLIENT_WRITE_REQUEST);
                request.setData(data);
            }


            // Wait until response
            synchronized (proxyResponse) {
                sendRequest(request);
                TaoLogger.logForce("Waiting for response");
                // This is used to wait for the proxy to respond
                proxyResponse.wait();
                TaoLogger.logForce("Done waiting");
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
    protected void sendRequest(ClientRequest request) {
        try {
            // Send request to proxy
            byte[] serializedRequest = request.serialize();
            byte[] requestHeader = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
            ByteBuffer requestMessage = ByteBuffer.wrap(Bytes.concat(requestHeader, serializedRequest));
            TaoLogger.logForce("In sendRequest, just about to send actual message to the proxy");


            synchronized (mChannel) {
                while (requestMessage.remaining() > 0) {
                    Future<Integer> writeResult = mChannel.write(requestMessage);
                    writeResult.get();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Private helper method that will wait for proxy responses
     */
    private void listenForResponse(Object listenerWait) {
        // Create runnable to listen for a ProxyResponse
        Runnable r = () -> {
            try {
                // Create an asynchronous channel to listen for connections
                TaoLogger.logForce("Waiting for a connection");
                AsynchronousServerSocketChannel channel =
                        AsynchronousServerSocketChannel.open(mThreadGroup).bind(new InetSocketAddress(mClientAddress.getPort()));
                // Asynchronously wait for incoming connections
                TaoLogger.logForce("Waiting for a connection");
                channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                    @Override
                    public void completed(AsynchronousSocketChannel proxyChannel, Void att) {
                        // Start listening for other connections
                        channel.accept(null, this);

                        TaoLogger.logForce("Just made a connection");

                        Runnable serializeProcedure = () -> serveProxy(proxyChannel);
                        new Thread(serializeProcedure).start();
                    }

                    @Override
                    public void failed(Throwable exc, Void att) {
                        // TODO: implement?
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }

            synchronized (listenerWait) {
                listenerWait.notify();
            }
        };

        // Start runnable on new thread
        new Thread(r).start();
    }

    private void serveProxy(AsynchronousSocketChannel channel) {
        try {
            // Create a ByteBuffer to read in message type
            ByteBuffer typeByteBuffer = MessageUtility.createTypeReceiveBuffer();

            // Asynchronously read message
            channel.read(typeByteBuffer, null, new CompletionHandler<Integer, Void>() {
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
                        channel.read(messageByteBuffer, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result, Void attachment) {
                                TaoLogger.log("Read at least some of message");
                                // Make sure we read all the bytes
                                while (messageByteBuffer.remaining() > 0) {
                                    TaoLogger.log("Going to read more of message");
                                    channel.read(messageByteBuffer, null, this);
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
                                    TaoLogger.logForce("Got response to request #" + proxyResponse.getClientRequestID());
                                    mResponseWaitMap.remove(proxyResponse.getClientRequestID());
                                    serveProxy(channel);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public Future<byte[]> readAsync(long blockID) {
        Callable<byte[]> readTask = () -> {
            // Send read request
            ProxyResponse response = sendRequest(MessageTypes.CLIENT_READ_REQUEST, blockID, null);
            return response.getReturnData();
        };

        Future<byte[]> future = mExecutor.submit(readTask);

        return future;
    }

    @Override
    public Future<Boolean> writeAsync(long blockID, byte[] data) {
        Callable<Boolean> readTask = () -> {
            // Send write request
            TaoLogger.logForce("Doing something here skeddit");
            ProxyResponse response = sendRequest(MessageTypes.CLIENT_WRITE_REQUEST, blockID, data);
            return response.getWriteStatus();
        };

        Future<Boolean> future = mExecutor.submit(readTask);

        return future;
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
        Random r = new Random();

        int numDataItems = 1000;

        // Do a write for numDataItems blocks
        long blockID;
        ArrayList<byte[]> listOfBytes = new ArrayList<>();

        boolean writeStatus;
        for (int i = 1; i <= numDataItems; i++) {
            TaoLogger.logForce("Doing a write for block " + i);
            blockID = i;
            byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
            Arrays.fill(dataToWrite, (byte) blockID);
            listOfBytes.add(dataToWrite);
            writeStatus = client.write(blockID, dataToWrite);

            if (!writeStatus) {
                TaoLogger.log("Write failed for block " + i);
                System.exit(1);
            } else {
                TaoLogger.logForce("Write was successful for " + i);
            }
        }

        int readOrWrite;
        int targetBlock;
        byte[] z;
        TaoLogger.logForce2("Going to start load test");
        for (int i = 0; i < 1000; i++) {
            readOrWrite = r.nextInt(2);



            targetBlock = r.nextInt(numDataItems) + 1;
            if (readOrWrite == 0) {
                TaoLogger.logForce2("Doing read request #" + mRequestID);
                z = client.read(targetBlock);

                if (!Arrays.equals(listOfBytes.get(targetBlock-1), z)) {
                    TaoLogger.logForce("Read failed for block " + targetBlock);
                    System.exit(1);
                }
            } else {
                TaoLogger.logForce2("Doing write request #" + mRequestID);
                writeStatus = client.write(targetBlock, listOfBytes.get(targetBlock - 1));

                if (!writeStatus) {
                    TaoLogger.logForce("Write failed for block " + targetBlock);
                    System.exit(1);
                }
            }
        }
        TaoLogger.logForce2("Ending load test");
    }

    public static void loadTestAsync(Client client) {
        Random r = new Random();

        int numDataItems = 100;

        // Do a write for numDataItems blocks
        long blockID;
        ArrayList<byte[]> listOfBytes = new ArrayList<>();

        boolean writeStatus;
        for (int i = 1; i <= numDataItems; i++) {
            TaoLogger.logForce("Doing a write for block " + i);
            blockID = i;
            byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
            Arrays.fill(dataToWrite, (byte) blockID);
            listOfBytes.add(dataToWrite);
            client.writeAsync(blockID, dataToWrite);
//            writeStatus = client.write(blockID, dataToWrite);
//
//            if (!writeStatus) {
//                TaoLogger.log("Write failed for block " + i);
//                System.exit(1);
//            } else {
//                TaoLogger.logForce("Write was successful for " + i);
//            }
        }

        //   client.printSubtree();

        int readOrWrite;
        int targetBlock;
        byte[] z;
        TaoLogger.logForce2("Going to start load test");
        for (int i = 0; i < 1000; i++) {
            readOrWrite = r.nextInt(2);



            targetBlock = r.nextInt(numDataItems) + 1;
            if (readOrWrite == 0) {
                TaoLogger.logForce2("Doing read request #" + mRequestID);
                z = client.read(targetBlock);

                if (!Arrays.equals(listOfBytes.get(targetBlock-1), z)) {
                    TaoLogger.logForce("Read failed for block " + targetBlock);
                    System.exit(1);
                }
            } else {
                TaoLogger.logForce2("Doing write request #" + mRequestID);
                writeStatus = client.write(targetBlock, listOfBytes.get(targetBlock - 1));

                if (!writeStatus) {
                    TaoLogger.logForce("Write failed for block " + targetBlock);
                    System.exit(1);
                }
            }
        }
        TaoLogger.logForce2("Ending load test");
    }

    public static void main(String[] args) {
        try {
            TaoLogger.logOn = false;
            long systemSize = 246420;
            TaoClient client = new TaoClient();
            TaoLogger.log("Going to start load test");
            loadTest(client);
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

                    if (!writeStatus) {
                        TaoLogger.logForce("Write failed");
                        System.exit(1);
                    }
                } else if (option.equals("R")) {
                    TaoLogger.logForce("Enter block ID to read from");

                    long blockID = reader.nextLong();

                    TaoLogger.logForce("Going to send read request for " + blockID);
                    byte[] result = client.read(blockID);

                    TaoLogger.logForce("The result of the read is a block filled with the number " + result[0]);
                    TaoLogger.logForce("Last number in the block is  " + result[result.length - 1]);
                } else if (option.equals("P")) {
                    client.printSubtree();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.exit(1);
    }
}
