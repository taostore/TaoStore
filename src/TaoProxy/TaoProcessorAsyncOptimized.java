package TaoProxy;

import Configuration.TaoConfigs;
import Messages.*;
import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @brief An implementation of the TaoProcessor that is optimized for batch asynchronous operations
 */
public class TaoProcessorAsyncOptimized extends TaoProcessor {
    /**
     * @brief Constructor
     * @param proxy
     * @param sequencer
     * @param threadGroup
     * @param messageCreator
     * @param pathCreator
     * @param cryptoUtil
     * @param subtree
     * @param positionMap
     * @param relativeMapper
     */
    public TaoProcessorAsyncOptimized(Proxy proxy, Sequencer sequencer, AsynchronousChannelGroup threadGroup, MessageCreator messageCreator, PathCreator pathCreator, CryptoUtil cryptoUtil, Subtree subtree, PositionMap positionMap, Map<Long, Long> relativeMapper) {
        super(proxy, sequencer, threadGroup, messageCreator, pathCreator, cryptoUtil, subtree, positionMap, relativeMapper);
    }

    @Override
    public void readPath(ClientRequest req) {
        try {
            TaoLogger.logInfo("Starting a readPath for blockID " + req.getBlockID());

            // Create new entry into response map for this request
            mResponseMap.put(req, new ResponseMapEntry());

            // Variables needed for fake read check
            boolean fakeRead;
            long pathID;

            // We make sure the request list and read/write lock for maps are not null
            List<ClientRequest> requestList = new ArrayList<>();
            ReentrantReadWriteLock requestListLock = new ReentrantReadWriteLock();
            mRequestMap.putIfAbsent(req.getBlockID(), requestList);
            mRequestLockMap.putIfAbsent(req.getBlockID(), requestListLock);
            requestList = mRequestMap.get(req.getBlockID());
            requestListLock = mRequestLockMap.get(req.getBlockID());

            // Acquire a read lock to ensure we do not assign a fake read that will not be answered to
            requestListLock.readLock().lock();

            // Check if there is any current request for this block ID
            if (requestList.isEmpty()) {
                // If no other requests for this block ID have been made, it is not a fake read
                fakeRead = false;

                // Find the path that this block maps to
                pathID = mPositionMap.getBlockPosition(req.getBlockID());

                // If pathID is -1, that means that this blockID is not yet mapped to a path
                if (pathID == -1) {
                    // Fetch a random path from server
                    pathID = mCryptoUtil.getRandomPathID();
                }
            } else {
                // There is currently a request for the block ID, so we need to trigger a fake read
                fakeRead = true;

                // Fetch a random path from server
                pathID = mCryptoUtil.getRandomPathID();
            }

            // Add request to request map list
            requestList.add(req);

            // Unlock
            requestListLock.readLock().unlock();

            TaoLogger.logInfo("Doing a read for pathID " + pathID);

            // Insert request into mPathReqMultiSet to make sure that this path is not deleted before this response
            // returns from server
            mPathReqMultiSet.add(pathID);

            // Create effectively final variables to use for inner classes
            long relativeFinalPathID = mRelativeLeafMapper.get(pathID);
            long absoluteFinalPathID = pathID;


            // Get the particular server InetSocketAddress that we want to connect to
            InetSocketAddress targetServer = mPositionMap.getServerForPosition(pathID);

            // Get the channel to that server
            AsynchronousSocketChannel channelToServer = AsynchronousSocketChannel.open(mThreadGroup);

            // Create a read request to send to server
            ProxyRequest proxyRequest = mMessageCreator.createProxyRequest();
            proxyRequest.setPathID(relativeFinalPathID);
            proxyRequest.setType(MessageTypes.PROXY_READ_REQUEST);

            // Serialize request
            byte[] requestData = proxyRequest.serialize();

            channelToServer.connect(targetServer, null, new CompletionHandler<Void, Object>() {
                @Override
                public void completed(Void result, Object attachment) {
                    // First we send the message type to the server along with the size of the message
                    byte[] messageType = MessageUtility.createMessageHeaderBytes(MessageTypes.PROXY_READ_REQUEST, requestData.length);
                    ByteBuffer entireMessage = ByteBuffer.wrap(Bytes.concat(messageType, requestData));

                    // Asynchronously send message type and length to server
                    channelToServer.write(entireMessage, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            // Make sure we write the whole message
                            while (entireMessage.remaining() > 0) {
                                channelToServer.write(entireMessage, null, this);
                                return;
                            }

                            // Asynchronously read response type and size from server
                            ByteBuffer messageTypeAndSize = MessageUtility.createTypeReceiveBuffer();

                            channelToServer.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {
                                @Override
                                public void completed(Integer result, Void attachment) {
                                    // Make sure we read the entire message
                                    while (messageTypeAndSize.remaining() > 0) {
                                        channelToServer.read(messageTypeAndSize, null, this);
                                        return;
                                    }

                                    // Flip the byte buffer for reading
                                    messageTypeAndSize.flip();

                                    // Parse the message type and size from server
                                    int[] typeAndLength = MessageUtility.parseTypeAndLength(messageTypeAndSize);
                                    int messageType = typeAndLength[0];
                                    int messageLength = typeAndLength[1];

                                    // Asynchronously read response from server
                                    ByteBuffer pathInBytes = ByteBuffer.allocate(messageLength);
                                    channelToServer.read(pathInBytes, null, new CompletionHandler<Integer, Void>() {
                                        @Override
                                        public void completed(Integer result, Void attachment) {
                                            // Make sure we read all the bytes for the path
                                            while (pathInBytes.remaining() > 0) {
                                                channelToServer.read(pathInBytes, null, this);
                                                return;
                                            }

                                            // Flip the byte buffer for reading
                                            pathInBytes.flip();

                                            // Serve message based on type
                                            if (messageType == MessageTypes.SERVER_RESPONSE) {
                                                // Get message bytes
                                                byte[] serialized = new byte[messageLength];
                                                pathInBytes.get(serialized);

                                                // Create ServerResponse object based on data
                                                ServerResponse response = mMessageCreator.createServerResponse();
                                                response.initFromSerialized(serialized);

                                                // Set absolute path ID
                                                response.setPathID(absoluteFinalPathID);

                                                // Close channel
                                                try {
                                                    channelToServer.close();
                                                } catch (IOException e) {
                                                    e.printStackTrace();
                                                }

                                                // Send response to proxy
                                                Runnable serializeProcedure = () -> mProxy.onReceiveResponse(req, response, fakeRead);
                                                new Thread(serializeProcedure).start();
                                            }
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
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Object attachment) {

                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
