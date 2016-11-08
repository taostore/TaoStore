package TaoProxy;

import Configuration.TaoConfigs;
import Messages.*;
import com.google.common.primitives.Bytes;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @brief The Sequencer makes sure that replies are sent to the client in the same order that requests were received
 */
public class TaoSequencer implements Sequencer {
    // Size of request queue
    private final static int QUEUE_SIZE = 100000;

    // Map that will map each request to the value of the requested block
    // The value will be null if the reply to this request has not yet been received
    private Map<ClientRequest, Block> mRequestMap;

    // Queue of the received requests
    private BlockingQueue<ClientRequest> mRequestQueue;

    private PathCreator mBlockCreator;
    private MessageCreator mMessageCreator;

    // The channel group used for asynchronous socket
    private AsynchronousChannelGroup mThreadGroup;

    private Map<String, Map<InetSocketAddress, AsynchronousSocketChannel>> mProxyToServerMap;
    private Map<InetSocketAddress, AsynchronousSocketChannel> mChannelMap;

    /**
     * @brief Default constructor for the TaoStore Sequencer
     */
    public TaoSequencer(MessageCreator messageCreator, PathCreator pathCreator) {
        try {
            mMessageCreator = messageCreator;
            mBlockCreator = pathCreator;

            // NOTE: ConcurrentHashMap is weakly consistent amongst different threads. Should be fine in this scenario
            mRequestMap = new ConcurrentHashMap<>();
            mRequestQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);

            // Create thread group
            mChannelMap = new HashMap<>();
            // Run the serialize procedure in a different thread
            Runnable serializeProcedure = this::serializationProcedure;
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            new Thread(serializeProcedure).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReceiveRequest(ClientRequest req) {
        try {
            // Create an empty block with null data
            Block empty = mBlockCreator.createBlock();
            empty.setData(null);

            // Put request and new empty block into request map
            synchronized (mRequestMap) {
                mRequestMap.put(req, empty);
            }

            // Add this request to the request queue
            mRequestQueue.add(req);


            if (mChannelMap.get(req.getClientAddress()) == null || req.getRequestID() == 0) {
                AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
                Future connection = channel.connect(req.getClientAddress());
                connection.get();
                mChannelMap.put(req.getClientAddress(), channel);
            }

//            if (req.getRequestID() == 0) {
//                TaoLogger.logForce("Trying to connect to client listener");
//                AsynchronousSocketChannel channel = mChannelMap.get(req.getClientAddress());
//                channel.close();
//                AsynchronousSocketChannel channel2 = AsynchronousSocketChannel.open(mThreadGroup);
//                Future connection = channel2.connect(req.getClientAddress());
//                connection.get();
//                mChannelMap.replace(req.getClientAddress(), channel2);
//
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onReceiveResponse(ClientRequest req, ServerResponse resp, byte[] data) {
        try {
            // Create a new block and set the data
            Block b = mBlockCreator.createBlock();
            b.setData(data);

            if (data == null) {
                TaoLogger.logForce("THIS DATA IS NULL FOR SOME REASON");
            } else
            {
                TaoLogger.logForce("THIS DATA NOT NULL");

            }
            // Replace empty null block with new block
            synchronized (mRequestMap) {
                mRequestMap.replace(req, b);
                mRequestMap.notify();
            }

            TaoLogger.logForceWithReqID("Just finished sequencer onReceiveResponse for " + req.getRequestID(), req.getRequestID());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void serializationProcedure() {
        // Run forever
        while (true) {
            try {
                // Retrieve request from request queue
                // Blocks if there is no item in queue
                ClientRequest req = mRequestQueue.take();


                // Wait until the reply for req comes back
                byte[] check;
                synchronized (mRequestMap) {
                    check = mRequestMap.get(req).getData();
                    while (check == null) {
                        mRequestMap.wait();
                        check = mRequestMap.get(req).getData();
                    }
                }

                TaoLogger.logForce("skeddit Sequencer going to send response for " + req.getRequestID() );

                // Create a ProxyResponse based on type of request
                ProxyResponse response = null;
                if (req.getType() == MessageTypes.CLIENT_READ_REQUEST) {
                    response = mMessageCreator.createProxyResponse();
                    response.setClientRequestID(req.getRequestID());
                    response.setReturnData(mRequestMap.get(req).getData());
                } else if (req.getType() == MessageTypes.CLIENT_WRITE_REQUEST) {
                    response = mMessageCreator.createProxyResponse();
                    response.setClientRequestID(req.getRequestID());
                    response.setWriteStatus(true);
                }


                InetSocketAddress hostAddress = req.getClientAddress();
                AsynchronousSocketChannel clientChannel = mChannelMap.get(hostAddress);

                if (clientChannel.isOpen()) {
                    TaoLogger.logForce("This channel is still open");
                } else {
                    TaoLogger.logForce("This channel is not open");
                }

                byte[] serializedResponse = response.serialize();

                // Create a read request to send to server
                TaoLogger.log("About to make message");
                byte[] header = MessageUtility.createMessageHeaderBytes(MessageTypes.PROXY_RESPONSE, serializedResponse.length);
                ByteBuffer fullMessage = ByteBuffer.wrap(Bytes.concat(header, serializedResponse));
                TaoLogger.log("About to send message");



                synchronized (clientChannel) {
                    while (fullMessage.remaining() > 0) {
                        Future<Integer> writeResult = clientChannel.write(fullMessage);
                        writeResult.get();
                    }
                    fullMessage = null;

                    synchronized (mRequestMap) {
                        mRequestMap.remove(req);
                    }

//                    clientChannel.write(fullMessage, null, new CompletionHandler<Integer, Void>() {
//                        @Override
//                        public void completed(Integer result, Void attachment) {
//                            TaoLogger.logForce("Responded, wrote " + result + " bytes");
//                            if (fullMessage.remaining() > 0) {
//                                TaoLogger.logForce("did not send all the data, still have " + fullMessage.remaining());
//                                clientChannel.write(fullMessage, null, this);
//                                return;
//                            } else {
//                                TaoLogger.log("que");
//                            }
//
//                            // Remove request from request map
//                            synchronized (mRequestMap) {
//                                mRequestMap.remove(req);
//                            }
//                        }
//
//                        @Override
//                        public void failed(Throwable exc, Void attachment) {
//
//                        }
//                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
