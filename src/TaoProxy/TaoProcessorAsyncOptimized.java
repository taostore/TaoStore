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
import java.util.Map;

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

            // Check if there is any current request for this block ID
            if (mRequestMap.get(req.getBlockID()) == null || mRequestMap.get(req.getBlockID()).isEmpty()) {
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

            // Insert request into request map
            // Acquire read lock, as there may be a concurrent pruning of the map
            // Note that pruning is required or empty lists will never be removed from map
//            mRequestMapLock.readLock().lock();

            // Check to see if a list already exists for this block id, if not create it
            if (mRequestMap.get(req.getBlockID()) == null) {
                // List does not yet exist, so we create it
                ArrayList<ClientRequest> newList = new ArrayList<>();
                newList.add(req);
                mRequestMap.put(req.getBlockID(), newList);
            } else {
                // The list exists, so we just add the request to it
                mRequestMap.get(req.getBlockID()).add(req);

            }

            // Release read lock
//            mRequestMapLock.readLock().unlock();

            TaoLogger.logInfo("Doing a read for pathID " + pathID);

            // Insert request into mPathReqMultiSet to make sure that this path is not deleted before this response
            // returns from server
            mPathReqMultiSet.add(pathID);

            // Create effectively final variables to use for inner classes
            if (mRelativeLeafMapper == null) {
                TaoLogger.logDebug("This thing is null");
            } else {
                TaoLogger.logDebug("This is not null");
            }
            long relativeFinalPathID = mRelativeLeafMapper.get(pathID);
            long absoluteFinalPathID = pathID;


            // Get the particular server InetSocketAddress that we want to connect to
            InetSocketAddress targetServer = mPositionMap.getServerForPosition(pathID);

            // Get the channel to that server
            AsynchronousSocketChannel channelToServer = AsynchronousSocketChannel.open(mThreadGroup);

            // Get the serverTakenMap for this client, which will be used as a lock for the above channel when requests
            // from the same client arrive
            Map<InetSocketAddress, Boolean> serverTakenMap = mAsyncProxyToServerTakenMap.get(req.getClientAddress());

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
