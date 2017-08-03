package ReplicatedStorage.TaoProxy;

import Configuration.ArgumentParser;
import Configuration.TaoConfigs;
import Messages.*;
import ReplicatedStorage.Configuration.RSTaoConfigs;
import TaoProxy.*;
import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.ThreadLocalRandom;

import Heartbeats.Coordinator.*;

public class RSTaoProcessor extends TaoProcessor {

    // Used to hold the responses made by a read-path quorum of servers.
    protected Map<ClientRequest, List<ServerResponse>> mReadPathResponses;

    // Will be used to protect access to mReadPathResponses.
    protected final transient ReentrantLock mReadPathResponsesLock = new ReentrantLock();

    // Position map which keeps track of what leaf each block corresponds to
    protected RSTaoPositionMap mRSPositionMap;

    protected HeartbeatCoordinator mHeartbeatCoordinator;

    public RSTaoProcessor(Proxy proxy,
                          Sequencer sequencer,
                          AsynchronousChannelGroup threadGroup,
                          MessageCreator messageCreator,
                          PathCreator pathCreator,
                          CryptoUtil cryptoUtil,
                          Subtree subtree,
                          PositionMap positionMap,
                          Map<Long, Long> relativeMapper,
                          Profiler profiler) {
        super(proxy, sequencer, threadGroup, messageCreator, pathCreator, cryptoUtil, subtree, positionMap, relativeMapper, profiler);
        mReadPathResponses = new HashMap<>();
        mRSPositionMap = new RSTaoPositionMap(RSTaoConfigs.ALL_SERVERS);

        List<InetAddress> addrs = new ArrayList<>();
        for (List<InetSocketAddress> replicaServers : RSTaoConfigs.ALL_SERVERS.values()) {
            for (InetSocketAddress address : replicaServers) {
                System.out.println(address);
                addrs.add(address.getAddress());
            }
        }
        mHeartbeatCoordinator = new HeartbeatCoordinator(addrs, 500);
        Runnable heartbeatProcedure = () -> mHeartbeatCoordinator.start();
        new Thread(heartbeatProcedure).start();
    }


    /**
     * @param addr
     * @brief Private helper method to make a map of server addresses to channels for addr
     */
    @Override
    protected void makeInitialConnections(InetSocketAddress addr) {



        try {
            // Get the number of storage servers
            int numServers = TaoConfigs.PARTITION_SERVERS.size();

            // Create a new map
            Map<InetSocketAddress, AsynchronousSocketChannel> newMap = new HashMap<>();

            // Atomically add channel map if not present
            mProxyToServerChannelMap.putIfAbsent(addr, newMap);

            // Get map in case two threads attempted to add the map at the same time
            newMap = mProxyToServerChannelMap.get(addr);

            // Claim map
            synchronized (newMap) {
                // Check if another thread has already added channels
                if (newMap.size() != numServers) {
                    // Create the taken semaphore for this addr
                    Map<InetSocketAddress, Semaphore> newSemaphoreMap = new ConcurrentHashMap<>();
                    mAsyncProxyToServerSemaphoreMap.put(addr, newSemaphoreMap);

                    // Create the channels to the storage servers
                    for (List<InetSocketAddress> replicaServers : RSTaoConfigs.ALL_SERVERS.values()) {
                        for (InetSocketAddress address : replicaServers) {
                            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
                            Future connection = channel.connect(address);
                            connection.get();

                            // Add the channel to the map
                            newMap.put(address, channel);

                            // Add a new semaphore for this channel to the map
                            newSemaphoreMap.put(address, new Semaphore(1));
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void onReceiveReadPathResponse(ClientRequest req,
                                             ServerResponse resp,
                                             boolean fakeRead) {
        try {

            List<ServerResponse> responses;

            synchronized (mReadPathResponses) {

                responses = mReadPathResponses.getOrDefault(req, null);
                if (responses == null) {
                    responses = new ArrayList<>();
                }
                responses.add(resp);

                mReadPathResponses.put(req, responses);

            }

            TaoLogger.logDebug("Number of responses: " + mReadPathResponses.get(req).size());

            if (responses.size() == RSTaoConfigs.READ_PATH_QUORUM_SIZE) {

                ArrayList<Path> responsePaths = new ArrayList<Path>();

                for (ServerResponse serverResponse : responses) {
                    byte[] encryptedPathBytes = serverResponse.getPathBytes();
                    if (encryptedPathBytes == null) {
                        for (ServerResponse serverResponse2 : responses) {
                            TaoLogger.logForce(serverResponse2.toString() + ", " + serverResponse2.getPathID() + ", " + serverResponse2.getWriteStatus());
                        }
                    }
                    Path decryptedPath = mCryptoUtil.decryptPath(encryptedPathBytes);
                    decryptedPath.setPathID(serverResponse.getPathID());
                    responsePaths.add(decryptedPath);
                }

                TaoPath newPath = selectBuckets(responsePaths);
                resp.setPathBytes(mCryptoUtil.encryptPath(newPath));

                mProfiler.readPathComplete(req);

                // Send response to proxy
                Runnable serializeProcedure = () -> mProxy.onReceiveResponse(req, resp, fakeRead);
                new Thread(serializeProcedure).start();

                synchronized (mReadPathResponses) {
                    mReadPathResponses.remove(req);
                }
            }



        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void readPath(ClientRequest req) {
        mProfiler.readPathStart(req);

        try {
            TaoLogger.logInfo("Starting a readPath for blockID " + req.getBlockID() + " and request #" + req.getRequestID());

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

            TaoLogger.logDebug("Doing a read for pathID " + pathID);

            // Insert request into mPathReqMultiSet to make sure that this path is not deleted before this response
            // returns from server
            mPathReqMultiSet.add(pathID);

            // Create effectively final variables to use for inner classes
            long relativeFinalPathID = mRelativeLeafMapper.get(pathID);
            long absoluteFinalPathID = pathID;

            // Get the map for particular client that maps the client to the channels connected to the server
            Map<InetSocketAddress, AsynchronousSocketChannel> mChannelMap = mProxyToServerChannelMap.get(req.getClientAddress());

            // If this is there first time the client has connected, the map may not have been made yet
            if (mChannelMap == null) {
                TaoLogger.logInfo("Going to make the initial connections for " + req.getClientAddress().getHostName());
                makeInitialConnections(req.getClientAddress());
            }

            // Get it once more in case it was null the first time
            mChannelMap = mProxyToServerChannelMap.get(req.getClientAddress());

            // Do this to make sure we wait until connections are made if this is one of the first requests made by this
            // particular client
            if (mChannelMap.size() < TaoConfigs.PARTITION_SERVERS.size()) {
                makeInitialConnections(req.getClientAddress());
            }

            // Get a quorum of server InetSocketAddress that we want to connect to
            List<InetSocketAddress> targetServers =
                    chooseQuorum(mRSPositionMap.getServersForPosition(pathID), RSTaoConfigs.READ_PATH_QUORUM_SIZE);

            TaoLogger.logInfo("[Req: " + req + "] Selected quorum: " + targetServers);

            for (InetSocketAddress targetServer : targetServers) {

                // Get the channel to that server
                AsynchronousSocketChannel channelToServer = mChannelMap.get(targetServer);

                // Get the serverSemaphoreMap for this client, which will be used to control access to the dedicated channel
                // when requests from the same client arrive for the same server
                Map<InetSocketAddress, Semaphore> serverSemaphoreMap = mAsyncProxyToServerSemaphoreMap.get(req.getClientAddress());

                // Create a read request to send to server
                ProxyRequest proxyRequest = mMessageCreator.createProxyRequest();
                proxyRequest.setPathID(relativeFinalPathID);
                proxyRequest.setType(MessageTypes.PROXY_READ_REQUEST);

                // Serialize request
                byte[] requestData = proxyRequest.serialize();

                mProfiler.readPathPreSend(targetServer, req);

                // Claim either the dedicated channel or create a new one if the dedicated channel is already being used
                if (serverSemaphoreMap.get(targetServer).tryAcquire()) {
                    // First we send the message type to the server along with the size of the message
                    byte[] messageType = MessageUtility.createMessageHeaderBytes(MessageTypes.PROXY_READ_REQUEST, requestData.length);
                    ByteBuffer entireMessage = ByteBuffer.wrap(Bytes.concat(messageType, requestData));

                    // Asynchronously send message type and length to server
                    channelToServer.write(entireMessage, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result,
                                              Void attachment) {
                            // Make sure we write the whole message
                            while (entireMessage.remaining() > 0) {
                                channelToServer.write(entireMessage, null, this);
                                return;
                            }

                            // Asynchronously read response type and size from server
                            ByteBuffer messageTypeAndSize = MessageUtility.createTypeReceiveBuffer();

                            channelToServer.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {
                                @Override
                                public void completed(Integer result,
                                                      Void attachment) {
                                    // profiling
                                    mProfiler.readPathPostRecv(targetServer, req);

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
                                        public void completed(Integer result,
                                                              Void attachment) {
                                            // Make sure we read all the bytes for the path
                                            while (pathInBytes.remaining() > 0) {
                                                channelToServer.read(pathInBytes, null, this);
                                                return;
                                            }

                                            // Release the semaphore for this server's dedicated channel
                                            serverSemaphoreMap.get(targetServer).release();

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

                                                long serverProcessingTime = response.getProcessingTime();
                                                mProfiler.readPathServerProcessingTime(targetServer, req, serverProcessingTime);

                                                // Send response to proxy
                                                Runnable serializeProcedure = () -> onReceiveReadPathResponse(req, response, fakeRead);
                                                new Thread(serializeProcedure).start();
                                            }
                                        }

                                        @Override
                                        public void failed(Throwable exc,
                                                           Void attachment) {
                                        }
                                    });
                                }

                                @Override
                                public void failed(Throwable exc,
                                                   Void attachment) {
                                }
                            });
                        }

                        @Override
                        public void failed(Throwable exc,
                                           Void attachment) {
                        }
                    });
                } else {
                    // We could not claim the dedicated channel for this server, we will instead create a new channel

                    // Get the channel to that server
                    AsynchronousSocketChannel newChannelToServer = AsynchronousSocketChannel.open(mThreadGroup);

                    // Connect to server
                    newChannelToServer.connect(targetServer, null, new CompletionHandler<Void, Object>() {
                        @Override
                        public void completed(Void result,
                                              Object attachment) {
                            // First we send the message type to the server along with the size of the message
                            byte[] messageType = MessageUtility.createMessageHeaderBytes(MessageTypes.PROXY_READ_REQUEST, requestData.length);
                            ByteBuffer entireMessage = ByteBuffer.wrap(Bytes.concat(messageType, requestData));

                            // Asynchronously send message type and length to server
                            newChannelToServer.write(entireMessage, null, new CompletionHandler<Integer, Void>() {
                                @Override
                                public void completed(Integer result,
                                                      Void attachment) {
                                    // Make sure we write the whole message
                                    while (entireMessage.remaining() > 0) {
                                        newChannelToServer.write(entireMessage, null, this);
                                        return;
                                    }

                                    // Asynchronously read response type and size from server
                                    ByteBuffer messageTypeAndSize = MessageUtility.createTypeReceiveBuffer();

                                    newChannelToServer.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {
                                        @Override
                                        public void completed(Integer result,
                                                              Void attachment) {
                                            // profiling
                                            mProfiler.readPathPostRecv(targetServer, req);

                                            // Make sure we read the entire message
                                            while (messageTypeAndSize.remaining() > 0) {
                                                newChannelToServer.read(messageTypeAndSize, null, this);
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
                                            newChannelToServer.read(pathInBytes, null, new CompletionHandler<Integer, Void>() {
                                                @Override
                                                public void completed(Integer result,
                                                                      Void attachment) {
                                                    // Make sure we read all the bytes for the path
                                                    while (pathInBytes.remaining() > 0) {
                                                        newChannelToServer.read(pathInBytes, null, this);
                                                        return;
                                                    }

                                                    // Close channel
                                                    try {
                                                        newChannelToServer.close();
                                                    } catch (IOException e) {
                                                        e.printStackTrace();
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

                                                        long serverProcessingTime = response.getProcessingTime();
                                                        mProfiler.readPathServerProcessingTime(targetServer, req, serverProcessingTime);

                                                        // Send response to proxy
                                                        Runnable serializeProcedure = () -> onReceiveReadPathResponse(req, response, fakeRead);
                                                        new Thread(serializeProcedure).start();
                                                    }
                                                }

                                                @Override
                                                public void failed(Throwable exc,
                                                                   Void attachment) {
                                                }
                                            });
                                        }

                                        @Override
                                        public void failed(Throwable exc,
                                                           Void attachment) {
                                        }
                                    });
                                }

                                @Override
                                public void failed(Throwable exc,
                                                   Void attachment) {
                                }
                            });
                        }

                        @Override
                        public void failed(Throwable exc,
                                           Object attachment) {

                        }
                    });
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void writeBack(long timeStamp) {
        // Variable to keep track of the current mNextWriteBack
        long writeBackTime;

        // Check to see if a write back should be started
        if (mWriteBackCounter >= mNextWriteBack) {
            // Multiple threads might pass first condition, must acquire lock in order to be the thread that triggers
            // the write back
            if (mWriteBackLock.tryLock()) {
                // Theoretically could be rare condition when a thread acquires lock but another thread has already
                // acquired the lock and incremented mNextWriteBack, so make sure that condition still holds
                if (mWriteBackCounter >= mNextWriteBack) {
                    // Keep track of the time
                    writeBackTime = mNextWriteBack;

                    // Increment the next time we should write trigger write back
                    mNextWriteBack += TaoConfigs.WRITE_BACK_THRESHOLD;

                    // Unlock and continue with write back
                    mWriteBackLock.unlock();
                } else {
                    // Condition no longer holds, so unlock and return
                    mWriteBackLock.unlock();
                    return;
                }
            } else {
                // Another thread is going to execute write back for this current value of mNextWriteBack, so return
                return;
            }
        } else {
            return;
        }

        // Make another variable for the write back time because Java says so
        long finalWriteBackTime = writeBackTime;

        mProfiler.writeBackStart(finalWriteBackTime);

        try {
            // Create a map that will map each InetSocketAddress to a list of paths that will be written to it
            Map<InetSocketAddress, List<Long>> writebackMap = new HashMap<>();

            // Needed in order to clean up subtree later
            List<Long> allWriteBackIDs = new ArrayList<>();

            synchronized (mWriteQueue) {

                // Get the first TaoConfigs.WRITE_BACK_THRESHOLD path IDs from the mWriteQueue and place them in the map
                for (int i = 0; i < TaoConfigs.WRITE_BACK_THRESHOLD; i++) {
                    // Get a path ID
                    Long currentID;

                    currentID = mWriteQueue.remove();

                    TaoLogger.logInfo("Writeback for path id " + currentID + " with timestamp " + finalWriteBackTime);

                    // Select a quorum of servers that are responsible for this path
                    for (InetSocketAddress isa : chooseQuorum(mRSPositionMap.getServersForPosition(currentID), RSTaoConfigs.WRITE_BACK_QUORUM_SIZE)) {
                        // Add this path ID to the map
                        List<Long> temp = writebackMap.get(isa);
                        if (temp == null) {
                            temp = new ArrayList<>();
                            writebackMap.put(isa, temp);
                        }
                        temp.add(currentID);
                    }
                }

            }

            // Current storage server we are targeting (corresponds to index into list of storage servers)
            int serverIndex = -1;

            // List of all the servers that successfully returned
            boolean[] serverDidReturn = new boolean[writebackMap.keySet().size()];

            // When a response is received from a server, this lock must be obtained to modify serverDidReturn
            Object returnLock = new Object();

            // Deep copy of paths in subtree for writeback
            Map<InetSocketAddress, List<Path>> wbPaths = new HashMap<>();


            //TaoProxy.mSubtreeLock.lock();

            // Take the subtree writer's lock
            mSubtreeRWL.writeLock().lock();

            // Make a deep copy of the needed paths from the subtree
            for (InetSocketAddress serverAddr : writebackMap.keySet()) {

                // Get the list of paths to be written for the current server
                List<Long> writebackPaths = writebackMap.get(serverAddr);

                List<Path> paths = new ArrayList<Path>();

                for (int i = 0; i < writebackPaths.size(); i++) {
                    // Get path
                    Path p = mSubtree.getPath(writebackPaths.get(i));
                    if (p != null) {
                        // Set the path to correspond to the relative leaf ID as present on the server to be written to
                        p.setPathID(mRelativeLeafMapper.get(p.getPathID()));
                        TaoPath pathCopy = new TaoPath();
                        pathCopy.initFromPath(p);

                        /* Log which blocks are in the snapshot */
                        for (Bucket bucket : pathCopy.getBuckets()) {
                            for (Block b : bucket.getFilledBlocks()) {
                                TaoLogger.logBlock(b.getBlockID(), "snapshot add");
                            }
                        }

                        paths.add(pathCopy);
                        allWriteBackIDs.add(writebackPaths.get(i));
                    }
                }

                wbPaths.put(serverAddr, paths);
            }

            // Release the subtree writer's lock
            mSubtreeRWL.writeLock().unlock();

            // Now we will send the writeback request to each server
            for (InetSocketAddress serverAddr : wbPaths.keySet()) {
                // Increment and save current server index
                serverIndex++;
                final int serverIndexFinal = serverIndex;

                // Get the list of paths to be written for the current server
                List<Path> paths = wbPaths.get(serverAddr);

                // Get all the encrypted path data
                byte[] dataToWrite = null;
                int pathSize = 0;

                for (int i = 0; i < paths.size(); i++) {
                    // Get path
                    Path p = paths.get(i);
                    // If this is the first path, don't need to concat the data
                    if (dataToWrite == null) {
                        dataToWrite = mCryptoUtil.encryptPath(p);
                        pathSize = dataToWrite.length;
                    } else {
                        dataToWrite = Bytes.concat(dataToWrite, mCryptoUtil.encryptPath(p));
                    }
                }

                if (dataToWrite == null) {
                    serverDidReturn[serverIndexFinal] = true;
                    continue;
                }

                TaoLogger.logInfo("Going to do writeback");

                // Create the proxy write request
                ProxyRequest writebackRequest = mMessageCreator.createProxyRequest();
                writebackRequest.setType(MessageTypes.PROXY_WRITE_REQUEST);
                writebackRequest.setPathSize(pathSize);
                writebackRequest.setDataToWrite(dataToWrite);
                writebackRequest.setTimestamp(finalWriteBackTime);

                // Serialize the request
                byte[] encryptedWriteBackPaths = writebackRequest.serialize();

                // Create and run server
                Runnable writebackRunnable = () -> {
                    try {
                        TaoLogger.logDebug("Going to do writeback for server " + serverIndexFinal);

                        mProfiler.writeBackPreSend(serverAddr, finalWriteBackTime);

                        // Create channel and connect to server
                        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
                        Future connection = channel.connect(serverAddr);
                        connection.get();

                        // First we send the message type to the server along with the size of the message
                        ByteBuffer messageType = MessageUtility.createMessageHeaderBuffer(MessageTypes.PROXY_WRITE_REQUEST, encryptedWriteBackPaths.length);
                        Future sendHeader;
                        while (messageType.remaining() > 0) {
                            sendHeader = channel.write(messageType);
                            sendHeader.get();
                        }
                        messageType = null;

                        // Send writeback paths
                        ByteBuffer message = ByteBuffer.wrap(encryptedWriteBackPaths);
                        Future sendMessage;
                        while (message.remaining() > 0) {
                            sendMessage = channel.write(message);
                            sendMessage.get();
                        }
                        message = null;
                        TaoLogger.logDebug("Sent info, now waiting to listen for server " + serverIndexFinal);

                        // Listen for server response
                        ByteBuffer messageTypeAndSize = MessageUtility.createTypeReceiveBuffer();
                        channel.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result,
                                                  Void attachment) {
                                // profiling
                                mProfiler.writeBackPostRecv(serverAddr, finalWriteBackTime);

                                // Flip the byte buffer for reading
                                messageTypeAndSize.flip();

                                // Parse the message type and size from server
                                int[] typeAndLength = MessageUtility.parseTypeAndLength(messageTypeAndSize);
                                int messageType = typeAndLength[0];
                                int messageLength = typeAndLength[1];

                                // Determine behavior based on response
                                if (messageType == MessageTypes.SERVER_RESPONSE) {
                                    // Read the response
                                    ByteBuffer messageResponse = ByteBuffer.allocate(messageLength);
                                    channel.read(messageResponse, null, new CompletionHandler<Integer, Void>() {

                                        @Override
                                        public void completed(Integer result,
                                                              Void attachment) {
                                            // Make sure we read the entire message
                                            while (messageResponse.remaining() > 0) {
                                                channel.read(messageResponse);
                                            }

                                            try {
                                                channel.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }

                                            // Flip byte buffer for reading
                                            messageResponse.flip();

                                            // Get data from response
                                            byte[] serialized = new byte[messageLength];
                                            messageResponse.get(serialized);

                                            // Create ServerResponse based on data
                                            ServerResponse response = mMessageCreator.createServerResponse();
                                            response.initFromSerialized(serialized);

                                            long serverProcessingTime = response.getProcessingTime();
                                            mProfiler.writeBackServerProcessingTime(serverAddr, finalWriteBackTime, serverProcessingTime);

                                            // Check to see if the write succeeded or not
                                            if (response.getWriteStatus()) {
                                                // Acquire return lock
                                                synchronized (returnLock) {
                                                    // Set that this server did return
                                                    serverDidReturn[serverIndexFinal] = true;

                                                    // Check if all the servers have returned
                                                    boolean allReturn = true;
                                                    for (int n = 0; n < serverDidReturn.length; n++) {
                                                        if (!serverDidReturn[n]) {
                                                            allReturn = false;
                                                            break;
                                                        }
                                                    }

                                                    // If all the servers have successfully responded, we can delete nodes from subtree
                                                    if (allReturn) {

                                                        TaoLogger.logDebug("All servers returned for writeBack #: " + finalWriteBackTime);

                                                        mProfiler.writeBackComplete(finalWriteBackTime);

                                                        TaoLogger.logWarning("Write Back Operation Successful");

                                                        //TaoProxy.mSubtreeLock.lock();
                                                        // Iterate through every path that was written, check if there are any nodes
                                                        // we can delete
                                                        for (Long pathID : allWriteBackIDs) {
                                                            // Upon response, delete all nodes in subtree whose timestamp
                                                            // is <= timeStamp, and are not in mPathReqMultiSet
                                                            // TODO: should pass in entire mPathReqMultiSet instead
                                                            Set<Long> set = new HashSet<>();
                                                            for (Long l : mPathReqMultiSet.elementSet()) {
                                                                set.add(l);
                                                            }
                                                            mSubtree.deleteNodes(pathID, finalWriteBackTime, set);
                                                        }

                                                        //TaoProxy.mSubtreeLock.unlock();
                                                    }
                                                }
                                            } else {
                                            }
                                        }

                                        @Override
                                        public void failed(Throwable exc,
                                                           Void attachment) {
                                        }
                                    });
                                }
                            }

                            @Override
                            public void failed(Throwable exc,
                                               Void attachment) {
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                new Thread(writebackRunnable).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * "Combine" paths by comparing bucket version numbers and taking the data in the bucket with the highest
     * version number. It is assumed that all TaoPath in paths have the same height and are completely filled in.
     *
     * @param paths
     * @return the new path with the latest buckets from paths
     */
    private TaoPath selectBuckets(List<Path> paths) {

        TaoPath newPath = new TaoPath();

        for (int i = 0; i <= paths.get(0).getPathHeight(); i++) {

            // Find newest bucket in level i

            Bucket newestBucket = paths.get(0).getBucket(i);

            for (Path path : paths.subList(1, paths.size())) {
                if (path.getBucket(i).getUpdateTime() > newestBucket.getUpdateTime())
                    newestBucket = path.getBucket(i);
            }

            // Insert a copy of newestBucket into the new path

            TaoBucket taoBucket = new TaoBucket();
            taoBucket.initFromBucket(newestBucket);
            newPath.insertBucket(taoBucket, i);

        }

        return newPath;
    }

    public static void main(String[] args) {
        try {
            // Parse any passed in args
            Map<String, String> options = ArgumentParser.parseCommandLineArguments(args);

            // Determine if the user has their own configuration file name, or just use the default
            String configFileName = options.getOrDefault("config_file", TaoConfigs.USER_CONFIG_FILE);
            TaoConfigs.USER_CONFIG_FILE = configFileName;

            // Create proxy
            RSTaoProxy proxy = new RSTaoProxy(new TaoMessageCreator(), new TaoBlockCreator(), new TaoSubtree());

            // Initialize and run server
            proxy.initializeServer();
            TaoLogger.logForce("Finished init, running proxy");
            proxy.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * @param servers
     * @param quorumSize
     * @return
     */
    private List<InetSocketAddress> chooseQuorum(List<InetSocketAddress> servers, int quorumSize) {
        List<InetSocketAddress> quorum = new ArrayList<>();
        List<InetSocketAddress> serversCopy = new ArrayList<>(servers);

        while (quorum.size() < quorumSize) {
            int index = ThreadLocalRandom.current().nextInt(0, serversCopy.size());
            if (mHeartbeatCoordinator.isAvailable(serversCopy.get(index).getAddress())) {
                quorum.add(serversCopy.remove(index));
            } else {
                //System.out.println(serversCopy.get(index).getAddress() + " is not available");
            }
        }

        return quorum;
    }

}
