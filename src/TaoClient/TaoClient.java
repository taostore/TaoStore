package TaoClient;

import Configuration.TaoConfigs;
import Messages.*;
import TaoProxy.*;
import com.google.common.primitives.Bytes;

import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.*;

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

    // Map of request IDs to ProxyResponses. Used to differentiate which request a response is answering
    protected Map<Long, ProxyResponse> mResponseWaitMap;

    // Channel to proxy
    protected AsynchronousSocketChannel mChannel;

    // ExecutorService for async reads/writes
    protected ExecutorService mExecutor;

    // Port this client will use
    public static int CLIENT_PORT = 12337;

    /**
     * @brief Default constructor
     */
    public TaoClient() {
        try {
            // Get the current client's IP
            String currentIP = InetAddress.getLocalHost().getHostAddress();
            mClientAddress = new InetSocketAddress(currentIP, CLIENT_PORT);

            // Initialize proxy address
            TaoLogger.logForce("I think the hostname is " + TaoConfigs.PROXY_HOSTNAME);
            mProxyAddress = new InetSocketAddress(TaoConfigs.PROXY_HOSTNAME, TaoConfigs.PROXY_PORT);

            // Create message creator
            mMessageCreator = new TaoMessageCreator();

            // Initialize response wait map
            mResponseWaitMap = new ConcurrentHashMap<>();

            // Thread group used for asynchronous I/O
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Create and connect channel to proxy
            mChannel = AsynchronousSocketChannel.open(mThreadGroup);
            Future connection = mChannel.connect(mProxyAddress);
            connection.get();

            // Create executor
            mExecutor = Executors.newFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Create listener for proxy responses, wait until it is finished initializing
            Object listenerWait = new Object();
            synchronized (listenerWait) {
                listenForResponse(listenerWait);
                listenerWait.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @brief Constructor
     * @param messageCreator
     */
    public TaoClient(MessageCreator messageCreator) {
        try {
            // Get the current client's IP
            String currentIP = InetAddress.getLocalHost().getHostAddress();
            mClientAddress = new InetSocketAddress(currentIP, CLIENT_PORT);

            // Initialize proxy address
            TaoLogger.logForce("I think the hostname is " + TaoConfigs.PROXY_HOSTNAME);
            mProxyAddress = new InetSocketAddress(TaoConfigs.PROXY_HOSTNAME, TaoConfigs.PROXY_PORT);

            // Create message creator
            mMessageCreator = messageCreator;

            // Initialize response wait map
            mResponseWaitMap = new ConcurrentHashMap<>();

            // Thread group used for asynchronous I/O
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Create and connect channel to proxy
            mChannel = AsynchronousSocketChannel.open(mThreadGroup);
            Future connection = mChannel.connect(mProxyAddress);
            connection.get();

            // Create executor
            mExecutor = Executors.newFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Create listener for proxy responses, wait until it is finished initializing
            Object listenerWait = new Object();
            synchronized (listenerWait) {
                listenForResponse(listenerWait);
                listenerWait.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] read(long blockID) {
        try {
            // Make request
            ClientRequest request = makeRequest(MessageTypes.CLIENT_READ_REQUEST, blockID, null, null);

            // Send read request
            ProxyResponse response = sendRequestWait(request);

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
            // Make request
            ClientRequest request = makeRequest(MessageTypes.CLIENT_WRITE_REQUEST, blockID, data, null);

            // Send write request
            ProxyResponse response = sendRequestWait(request);

            // Return write status
            return response.getWriteStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * @brief Method to make a client request
     * @param type
     * @param blockID
     * @param data
     * @param extras
     * @return a client request
     * NOTE: This method is likely not thread safe on requestID. Suggest adding lock if you need it to be
     */
    protected ClientRequest makeRequest(int type, long blockID, byte[] data, List<Object> extras) {
        // Keep track of requestID and increment it
        long requestID = mRequestID;
        mRequestID++;

        // Create client request
        ClientRequest request = mMessageCreator.createClientRequest();
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

        return request;
    }


    /**
     * @brief Private helper method to send request to proxy and wait for response
     * @param request
     * @return ProxyResponse to request
     */
    protected ProxyResponse sendRequestWait(ClientRequest request) {
        try {
            // Create an empty response and put it in the mResponseWaitMap
            ProxyResponse proxyResponse = mMessageCreator.createProxyResponse();
            mResponseWaitMap.put(request.getRequestID(), proxyResponse);

            // Send request and wait until response
            synchronized (proxyResponse) {
                sendRequestToProxy(request);
                proxyResponse.wait();
            }

            // Return response
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
    protected void sendRequestToProxy(ClientRequest request) {
        try {
            // Send request to proxy
            byte[] serializedRequest = request.serialize();
            byte[] requestHeader = MessageUtility.createMessageHeaderBytes(request.getType(), serializedRequest.length);
            ByteBuffer requestMessage = ByteBuffer.wrap(Bytes.concat(requestHeader, serializedRequest));

            // Send message to proxy
            synchronized (mChannel) {
                TaoLogger.logDebug("Sending request #" + request.getRequestID());
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
                AsynchronousServerSocketChannel channel =
                        AsynchronousServerSocketChannel.open(mThreadGroup).bind(new InetSocketAddress(mClientAddress.getPort()));

                // Asynchronously wait for incoming connections
                channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                    @Override
                    public void completed(AsynchronousSocketChannel proxyChannel, Void att) {
                        // Start listening for other connections
                        channel.accept(null, this);

                        // Start up a new thread to serve this connection
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

    /**
     * @brief Method to serve a proxy connection
     * @param channel
     */
    private void serveProxy(AsynchronousSocketChannel channel) {
        try {
            TaoLogger.logError("\n Client has connection from proxy \n");
            // Create a ByteBuffer to read in message type
            ByteBuffer typeByteBuffer = MessageUtility.createTypeReceiveBuffer();

            // Asynchronously read message
            channel.read(typeByteBuffer, null, new CompletionHandler<Integer, Void>() {
                @Override
                public void completed(Integer result, Void attachment) {
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

                        // Do one last asynchronous read to get the rest of the message
                        channel.read(messageByteBuffer, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result, Void attachment) {
                                // Make sure we read all the bytes
                                while (messageByteBuffer.remaining() > 0) {
                                    channel.read(messageByteBuffer, null, this);
                                    return;
                                }
                                // Flip the byte buffer for reading
                                messageByteBuffer.flip();

                                // Get the rest of the bytes for the message
                                byte[] requestBytes = new byte[messageLength];
                                messageByteBuffer.get(requestBytes);

                                // Initialize ProxyResponse object based on read bytes
                                ProxyResponse proxyResponse = mMessageCreator.createProxyResponse();
                                proxyResponse.initFromSerialized(requestBytes);

                                // Get the ProxyResponse from map and initialize it
                                ProxyResponse clientAnswer = mResponseWaitMap.get(proxyResponse.getClientRequestID());
                                clientAnswer.initFromSerialized(requestBytes);

                                // Notify thread waiting for this response id
                                synchronized (clientAnswer) {
                                    clientAnswer.notifyAll();
                                    TaoLogger.logInfo("\n\nGot response to request #" + clientAnswer.getClientRequestID());
                                    mResponseWaitMap.remove(clientAnswer.getClientRequestID());
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
            // Make request
            ClientRequest request = makeRequest(MessageTypes.CLIENT_READ_REQUEST, blockID, null, null);

            // Send read request
            ProxyResponse response = sendRequestWait(request);
            return response.getReturnData();
        };

        Future<byte[]> future = mExecutor.submit(readTask);

        return future;
    }

    @Override
    public Future<Boolean> writeAsync(long blockID, byte[] data) {
        Callable<Boolean> readTask = () -> {
            // Make request
            ClientRequest request = makeRequest(MessageTypes.CLIENT_WRITE_REQUEST, blockID, data, null);

            // Send write request
            ProxyResponse response = sendRequestWait(request);
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

    /**
     * @brief Method to do a load test on proxy
     * @param client
     */
    public static void loadTest(Client client) {
        // Random number generator
        Random r = new Random();

        // Number of unique data items to operate on
        int numDataItems = 1000;

        // Do a write for numDataItems blocks
        long blockID;
        ArrayList<byte[]> listOfBytes = new ArrayList<>();

        boolean writeStatus;
        for (int i = 1; i <= numDataItems; i++) {
            TaoLogger.logInfo("Doing a write for block " + i);
            blockID = i;
            byte[] dataToWrite = new byte[TaoConfigs.BLOCK_SIZE];
            Arrays.fill(dataToWrite, (byte) blockID);
            listOfBytes.add(dataToWrite);
            writeStatus = client.write(blockID, dataToWrite);

            if (!writeStatus) {
                TaoLogger.logError("Write failed for block " + i);
                System.exit(1);
            } else {
                TaoLogger.logInfo("Write was successful for " + i);
            }
        }

        int readOrWrite;
        int targetBlock;
        byte[] z;
        TaoLogger.logForce("Going to start load test");

        for (int i = 0; i < 1000; i++) {
            readOrWrite = r.nextInt(2);
            targetBlock = r.nextInt(numDataItems) + 1;

            if (readOrWrite == 0) {
                TaoLogger.logInfo("Doing read request #" + mRequestID);
                z = client.read(targetBlock);

                if (!Arrays.equals(listOfBytes.get(targetBlock-1), z)) {
                    TaoLogger.logError("Read failed for block " + targetBlock);
                    System.exit(1);
                }
            } else {
                TaoLogger.logInfo("Doing write request #" + mRequestID);
                writeStatus = client.write(targetBlock, listOfBytes.get(targetBlock - 1));

                if (!writeStatus) {
                    TaoLogger.logError("Write failed for block " + targetBlock);
                    System.exit(1);
                }
            }
        }
        TaoLogger.logForce("Ending load test");
    }

    public static void main(String[] args) {
        try {
            TaoLogger.logLevel = TaoLogger.LOG_INFO;

            long systemSize = 246420;
            TaoClient client = new TaoClient();

           // loadTest(client);

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
