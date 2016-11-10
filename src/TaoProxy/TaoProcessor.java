package TaoProxy;

import Configuration.TaoConfigs;
import Messages.*;
import TaoClient.Client;
import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TaoProcessor implements Processor {
    // Stash to hold blocks
    private Stash mStash;

    // Map that maps a block ID to a list of requests for that block ID
    private Map<Long, List<ClientRequest>> mRequestMap;

    // Lock used to ensure that additions to the request map are not overridden by deletions in writeBack
    private final ReentrantReadWriteLock mRequestMapLock = new ReentrantReadWriteLock();

    // Map that maps client requests to a ResponseMapEntry, signifying whether or not a request has been received or not
    private Map<ClientRequest, ResponseMapEntry> mResponseMap;

    // MultiSet which keeps track of which paths have been requested but not yet returned
    // This is needed for write back, to know what should or should not be deleted from the subtree when write completes
    private Multiset<Long> mPathReqMultiSet;

    // Subtree
    private Subtree mSubtree;

    // Counter used to know when we should writeback
    private long mWriteBackCounter;

    // Used to keep track of when the next writeback should occur
    // When mWriteBackCounter == mNextWriteBack, a writeback should occur
    private long mNextWriteBack;

    // Used to make sure that the writeback is only executed by one thread
    private final transient ReentrantLock mWriteBackLock = new ReentrantLock();

    // Write queue used to store which paths should be sent to server on next writeback
    private Queue<Long> mWriteQueue;

    // Position map which keeps track of what leaf each block corresponds to
    private PositionMap mPositionMap;

    // Proxy that this processor belongs to
    private Proxy mProxy;

    // Sequencer that belongs to the proxy
    private Sequencer mSequencer;

    // The channel group used for asynchronous socket
    private AsynchronousChannelGroup mThreadGroup;

    // CryptoUtil used for encrypting and decrypting paths
    private CryptoUtil mCryptoUtil;

    // MessageCreator for creating different types of messages
    private MessageCreator mMessageCreator;

    // PathCreator responsible for making empty blocks, buckets, and paths
    private PathCreator mPathCreator;

    // A map that maps each leafID to the relative leaf ID it would have within a server partition
    private Map<Long, Long> mRelativeLeafMapper;

   // private Map<InetSocketAddress, AsynchronousSocketChannel> mChannelMap;

    private Map<InetSocketAddress, Map<InetSocketAddress, AsynchronousSocketChannel>> mProxyToServerChannelMap;
    private Map<InetSocketAddress, Map<InetSocketAddress, Boolean>> mAsyncProxyToServerTakenMap;
    /**
     * @brief Default constructor
     */
    public TaoProcessor(Proxy proxy, Sequencer sequencer, AsynchronousChannelGroup threadGroup, MessageCreator messageCreator, PathCreator pathCreator, CryptoUtil cryptoUtil, Subtree subtree, PositionMap positionMap) {
        try {
            mProxy = proxy;
            mSequencer = sequencer;

            // TODO: needed?
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(TaoConfigs.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            mMessageCreator = messageCreator;
            mPathCreator = pathCreator;
            mCryptoUtil = cryptoUtil;

            // Create stash
            // TODO: pass this in?
            mStash = new TaoStash();

            // Create request map
            mRequestMap = new HashMap<>();

            // Create response map
            mResponseMap = new ConcurrentHashMap<>();

            // Create requested path multiset
            mPathReqMultiSet = ConcurrentHashMultiset.create();

            // Due to JIT, we invoke a call to mPathReqMultiSet
            mPathReqMultiSet.add(-1L);
            mPathReqMultiSet.remove(-1L);

            // Due to JIT, we invoke the following call
            ArrayList<Block> blocksToFlush = new ArrayList<>();
            Lists.newArrayList(Sets.newHashSet(blocksToFlush));

            // Create subtree
            // TODO: pass this in?
            mSubtree = subtree;

            // Create counter the keep track of number of flushes
            mWriteBackCounter = 0;
            mNextWriteBack = TaoConfigs.WRITE_BACK_THRESHOLD;

            // Create list of queues of paths to be written
            // The index into the list corresponds to the server at that same index in TaoConfigs.PARTITION_SERVERS
            mWriteQueue = new ConcurrentLinkedQueue<>();

            // Create position map
            mPositionMap = positionMap;

            // Map each leaf to a relative leaf for the servers
            mRelativeLeafMapper = new HashMap<>();
            int numServers = TaoConfigs.PARTITION_SERVERS.size();
            int numLeaves = 1 << TaoConfigs.TREE_HEIGHT;
            int leavesPerPartition = numLeaves / numServers;
            for (int i = 0; i < numLeaves; i += leavesPerPartition) {
                long currentServerLeaves = i;
                long relativeLeaf = 0;
                while (currentServerLeaves < i + leavesPerPartition) {
                    // TaoLogger.logForce("1 Mapping absolute leaf " + currentServerLeaves + " to relative leaf " + relativeLeaf);
                    mRelativeLeafMapper.put(currentServerLeaves, relativeLeaf);
                    currentServerLeaves++;
                    relativeLeaf++;
                }
            }

            mProxyToServerChannelMap = new ConcurrentHashMap<>();
            mAsyncProxyToServerTakenMap = new ConcurrentHashMap<>();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void readPath(ClientRequest req) {
        try {
            TaoLogger.logForceWithReqID("--- Starting a readPath for blockID " + req.getBlockID(), req.getRequestID());
            // Create new entry into response map
            mResponseMap.put(req, new ResponseMapEntry());

            // Check if this current block ID has other previous requests
            boolean fakeRead;
            long pathID;
            // TODO: check if fake read is working
            // Check if there is any current request for this block ID
            if (mRequestMap.get(req.getBlockID()) == null || mRequestMap.get(req.getBlockID()).isEmpty()) {
                TaoLogger.log("There are no outstanding requests for " + req.getBlockID());
                // If no other requests for this block ID have been made, it is not a fake read
                fakeRead = false;

                // Find the path that this block maps to
                pathID = mPositionMap.getBlockPosition(req.getBlockID());

                // If pathID is -1, that means that this blockID is not yet mapped to a path
                if (pathID == -1) {
                    // Fetch a random path from server
                    pathID = mCryptoUtil.getRandomPathID();
                    TaoLogger.log("Finished assigning random path " + req.getBlockID());
                }

            } else {
                // There is currently a request for the block ID, so we need to trigger a fake read
                fakeRead = true;

                // Fetch a random path from server
                pathID = mCryptoUtil.getRandomPathID();
            }

            // Insert request into request map
            // Acquire read lock, as there may be a concurrent pruning of the map
            // Note that pruning is required or empty list will never be removed from map
            mRequestMapLock.readLock().lock();

            // Check to see if a list already exists for this block id, if not create it
            if (mRequestMap.get(req.getBlockID()) == null) {

                // List does not yet exist, so we create it
                ArrayList<ClientRequest> newList = new ArrayList<>();
                newList.add(req);
                mRequestMap.put(req.getBlockID(), newList);

            } else {

                mRequestMap.get(req.getBlockID()).add(req);

            }

            // Release read lock
            mRequestMapLock.readLock().unlock();

            TaoLogger.logForceWithReqID("Doing a read for path ID: " + pathID, req.getRequestID());

            // Insert request into mPathReqMultiSet to make sure that this path is not deleted before this response
            // returns from server
            mPathReqMultiSet.add(pathID);

            // Create effectively final variables to use for inner classes
            long relativeFinalPathID = mRelativeLeafMapper.get(pathID);
            long absoluteFinalPathID = pathID;


            // Get the map for particular client that maps the client to the channels connected to the server
            Map<InetSocketAddress, AsynchronousSocketChannel> mChannelMap = mProxyToServerChannelMap.get(req.getClientAddress());

            // If this is one of the first few clients, the map may not have been made yet
            if (mChannelMap == null) {
                TaoLogger.logForceWithReqID("Going to make the initial connections for " + req.getClientAddress().getHostName(), req.getRequestID());
                makeInitialConnections(req.getClientAddress());
            }

            // Get it once more in case it was null the first time
            mChannelMap = mProxyToServerChannelMap.get(req.getClientAddress());

            // Do this to wait until connections are made
            if (mChannelMap.size() < TaoConfigs.PARTITION_SERVERS.size()) {
                makeInitialConnections(req.getClientAddress());
            }

            // Get the particular server InetSocketAddress that we want to connect to
            InetSocketAddress targetServer = mPositionMap.getServerForPosition(pathID);

            // Get the channel to that server
            AsynchronousSocketChannel channelToServer = mChannelMap.get(targetServer);

            // Get the serverTakenMap for this client, which will be used as a lock for the above channel
            Map<InetSocketAddress, Boolean> serverTakenMap = mAsyncProxyToServerTakenMap.get(req.getClientAddress());

            // Create a read request to send to server
            ProxyRequest proxyRequest = mMessageCreator.createProxyRequest();
            proxyRequest.setPathID(relativeFinalPathID);
            proxyRequest.setType(MessageTypes.PROXY_READ_REQUEST);

            // Serialize request
            byte[] requestData = proxyRequest.serialize();

            TaoLogger.log("Begin sending read message");
            // Claim the channel
            synchronized (channelToServer) {

                // Check to see if the channel is being used. If it is, wait
                while (serverTakenMap.get(targetServer)) {
                    channelToServer.wait();
                }

                // Mark the channel serving this server as taken so no other thread can use the channel while
                // channel is being used
                serverTakenMap.replace(targetServer, true);

                // First we send the message type to the server along with the size of the message
                byte[] messageType = MessageUtility.createMessageHeaderBytes(MessageTypes.PROXY_READ_REQUEST, requestData.length);
                ByteBuffer entireMessage = ByteBuffer.wrap(Bytes.concat(messageType, requestData));

                // Asynchronously send message type and length to server
                channelToServer.write(entireMessage, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer result, Void attachment) {
                        // Asynchronously read response type and size from server
                        ByteBuffer messageTypeAndSize = MessageUtility.createTypeReceiveBuffer();
                        TaoLogger.log("Begin reading message");
                        channelToServer.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer result, Void attachment) {
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

                                            // Mark the channel as free
                                            synchronized (channelToServer) {
                                                serverTakenMap.replace(targetServer, false);
                                                channelToServer.notifyAll();
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void makeInitialConnections(InetSocketAddress addr) {
        try {
            int numServers = TaoConfigs.PARTITION_SERVERS.size();
            Map<InetSocketAddress, AsynchronousSocketChannel> newMap = new HashMap<>();

            // Atomically add channel map if not present
            mProxyToServerChannelMap.putIfAbsent(addr, newMap);
            newMap = mProxyToServerChannelMap.get(addr);
            synchronized (newMap) {
                if (newMap.size() != numServers) {
                    Map<InetSocketAddress, Boolean> newBooleanMap = new ConcurrentHashMap<>();
                    mAsyncProxyToServerTakenMap.put(addr, newBooleanMap);
                    for (int i = 0; i < numServers; i++) {
                        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
                        Future connection = channel.connect(TaoConfigs.PARTITION_SERVERS.get(i));
                        connection.get();

                        newMap.put(TaoConfigs.PARTITION_SERVERS.get(i), channel);
                        newBooleanMap.put(TaoConfigs.PARTITION_SERVERS.get(i), false);
                    }
                }
            }
            TaoLogger.logForce("outer map has size " + mProxyToServerChannelMap.size());
            TaoLogger.logForce("inner map has size " + mProxyToServerChannelMap.get(addr).size());
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void answerRequest(ClientRequest req, ServerResponse resp, boolean isFakeRead) {
        TaoLogger.logForceWithReqID("--- Going to answer request with requestID " + req.getRequestID(), req.getRequestID());

        // Get information about response
        boolean fakeRead = isFakeRead;

        // Decrypt response data
        byte[] encryptedPathBytes = resp.getPathBytes();
        Path decryptedPath = mCryptoUtil.decryptPath(encryptedPathBytes);

        // Set the correct path ID
        decryptedPath.setPathID(resp.getPathID());

        // Insert every bucket along path that is not in subtree into subtree
        // TODO: Is this making tree too large?
        // mSubtree.addPath(decryptedPath, mWriteBackCounter);
        mSubtree.addPath(decryptedPath, mWriteBackCounter);

        // Update the response map entry for this request
        ResponseMapEntry responseMapEntry = mResponseMap.get(req);
        responseMapEntry.setReturned(true);

        // Check if the data for this response entry is not null, which would be the case if the real read returned
        // before this fake read
        if (responseMapEntry.getData() != null) {
            // The real read has already appeared, so we can answer the client

            // Send the data to the sequencer
            TaoLogger.logForceWithReqID("Going to send response to sequencer, this was a fake read", req.getRequestID());
            mSequencer.onReceiveResponse(req, resp, responseMapEntry.getData());

            // Remove this request from the response map
            mResponseMap.remove(req);
            return;
        }

        // If the data has not yet returned, we check to see if this is the request that caused the real read for this block
        if (! fakeRead) {
            // Get a list of all the requests that have requested this block ID
            List<ClientRequest> requestList = mRequestMap.get(req.getBlockID());

            TaoLogger.logForceWithReqID("The content of the requestmap is ", req.getRequestID());
            for (ClientRequest s : requestList) {
                TaoLogger.logForceWithReqID("We got", s.getRequestID());
            }

            // Figure out if this is the first time the element has appeared
            // We need to know this because we need to know if we will be able to find this element in the path or subtree
            boolean elementDoesExist = mPositionMap.getBlockPosition(req.getBlockID()) != -1;
            boolean canPutInPositionMap = true;
            // Loop through each request in list of requests for this block
            while (!requestList.isEmpty()) {
                // Get current request that will be processed
                ClientRequest currentRequest = requestList.remove(0);
                responseMapEntry = mResponseMap.get(currentRequest);
                TaoLogger.logForceWithReqID("We are currently serving request " + currentRequest.getRequestID(), req.getRequestID());
                // Now we get the data from the desired block
                byte[] foundData;
                // First, from the subtree, find the bucket that has a block with blockID == req.getBlockID()
                if (elementDoesExist) {
                    TaoLogger.logForce("BlockID " + req.getBlockID() + " should exist somewhere");
                    // The element should exist somewhere
                    foundData = getDataFromBlock(currentRequest.getBlockID());
                } else {
                    TaoLogger.logForce("BlockID " + req.getBlockID() + " does not yet exist");
                    // The element has never been created before
                    foundData = new byte[TaoConfigs.BLOCK_SIZE];
                }

                // Check if the request was a write
                if (currentRequest.getType() == MessageTypes.CLIENT_WRITE_REQUEST) {
                    if (elementDoesExist) {
                        // The element should exist somewhere
                        writeDataToBlock(currentRequest.getBlockID(), currentRequest.getData());
                    } else {
                        Block newBlock = mPathCreator.createBlock();
                        newBlock.setBlockID(currentRequest.getBlockID());
                        newBlock.setData(currentRequest.getData());

                        // Add block to stash and assign random path position
                        mStash.addBlock(newBlock);
                    }
                    canPutInPositionMap = true;
                } else {
                    // If elementDoesExist == false and the request is not a write, we will not put assign this block ID
                    // a path in the position map
                    if (! elementDoesExist) {
                        // TODO: ProxyResponse should involve a failure flag for error
                        canPutInPositionMap = false;
                    }
                }
                // Check if the server has responded to this request yet
                // NOTE: This is the part that answers all fake reads
                responseMapEntry.setData(foundData);
                if (mResponseMap.get(currentRequest).getRetured()) {
                    // Send the data to sequencer
                    TaoLogger.logForceWithReqID("Going to send response to sequencer for " + currentRequest.getRequestID(), req.getRequestID());
                    mSequencer.onReceiveResponse(currentRequest, resp, foundData);

                    // Remove this request from the response map
                    mResponseMap.remove(currentRequest);
                }

                // After the first pass through the loop, the element is guaranteed to exist
                elementDoesExist = true;
            }

            if (canPutInPositionMap) {
                // Assign block with blockID == req.getBlockID() to a new random path in position map
                int newPathID = mCryptoUtil.getRandomPathID();
                TaoLogger.logForceWithReqID("%%%% Assigning blockID " + req.getBlockID() + " to path " + newPathID, req.getRequestID());
                mPositionMap.setBlockPosition(req.getBlockID(), newPathID);
            }
        } else {
            TaoLogger.logForceWithReqID("This is a fake read, real read has not yet returned", req.getRequestID());
        }

        // Now that the response has come back, remove one instance of the requested
        // block ID from mPathReqMultiSet
        // We have this here so that the path is not deleted while looking for block
        mPathReqMultiSet.remove(resp.getPathID());
    }

    /**
     * @brief Method to get data from a block with the given blockID
     * @param blockID
     * @return the data from block
     * TODO: Account for error
     */
    public byte[] getDataFromBlock(long blockID) {
        TaoLogger.logForce("$$ Trying to get data for blockID " + blockID);
        TaoLogger.logForce("I think this is at path: " + mPositionMap.getBlockPosition(blockID));

        // Due to multiple threads moving blocks around, we need to run this in a loop
        // TODO: This seems wrong, why? Possibly because a flush could remove a block? need locks? maybe already right?
        // TODO: likely because a block can be moved from the stash to the subtree during a concurrent flush (or vice versa)
        // TODO: solution, run the loop a few times before exiting?
        while (true) {
            // Check if the bucket containing this blockID is in the subtree
            // TODO: Issue does exist with concurrent flushes. Path might be cleared, removing mappings,
            // TODO: might call this before the
            Bucket targetBucket = mSubtree.getBucketWithBlock(blockID);
            if (targetBucket != null) {
                // If we found the bucket in the subtree, we can attempt to get the data from the block in bucket
                TaoLogger.log("Bucket containing block found in subtree");
                byte[] data = targetBucket.getDataFromBlock(blockID);

                // Check if this data is not null
                if (data != null) {
                    // If not null, we return the data
                    TaoLogger.log("$$ Returning data for block " + blockID);
                    return data;
                } else {
                    // If null, we exit
                    // TODO: change behavior
                    TaoLogger.logForce("scuba But bucket does not have the data we want");
                    mSubtree.printSubtree();
                    TaoLogger.logForce("Stash has");
                    ((TaoStash) mStash).printKeySet();
                    System.exit(1);
                    //continue;
                }
            } else {
                // If the block wasn't in the subtree, it should be in the stash
                TaoLogger.log("Cannot find in subtree");
                Block targetBlock = mStash.getBlock(blockID);

                if (targetBlock != null) {
                    // If we found the block in the stash, return the data
                    TaoLogger.log("$$ Returning data for block " + blockID);
                    return targetBlock.getData();
                } else {
                    // If we did not find the block, we exit
                    // TODO: change behavior
                    TaoLogger.logForce("scuba Cannot find in subtree or stash");
                    mSubtree.printSubtree();
                    TaoLogger.logForce("Stash has");
                    ((TaoStash) mStash).printKeySet();
                    System.exit(0);
                    //continue;
                }
            }
        }
    }

    /**
     * @brief Method to write data to a block with the given blockID
     * @param blockID
     * @param data
     */
    public void writeDataToBlock(long blockID, byte[] data) {
        TaoLogger.log("$$ Trying to write data for blockID " + blockID);
        TaoLogger.log("I think this is at path: " + mPositionMap.getBlockPosition(blockID));
        // Due to multiple threads moving blocks around, we need to run this in a loop
        while (true) {
            // Check if block is in subtree
            Bucket targetBucket = mSubtree.getBucketWithBlock(blockID);
            if (targetBucket != null) {
                // If the bucket was found, we modify a block
                if (targetBucket.modifyBlock(blockID, data)) {
                    return;
                }
            } else {
                // If we cannot find a bucket with the block, we check for the block in the stash
                Block targetBlock = mStash.getBlock(blockID);

                // If the block was found in the stash, we set the data for the block
                if (targetBlock != null) {
                    targetBlock.setData(data);
                    return;
                }
            }
        }
    }

    @Override
    public void flush(long pathID) {
        TaoLogger.logForce("--- Doing a flush for pathID " + pathID);
        // Increment the amount of times we have flushed
        mWriteBackCounter++;

        // Get path that will be flushed
        Path pathToFlush = mSubtree.getPath(pathID);

        // Lock every bucket on the path
        pathToFlush.lockPath();

        // Get a heap based on the block's path ID when compared to the target path ID
        PriorityQueue<Block> blockHeap = getHeap(pathID);

        // Clear path
        mSubtree.clearPath(pathID);

        // Variables to help with flushing path
        Block currentBlock;
        int level = TaoConfigs.TREE_HEIGHT;

        TaoLogger.logForce("About to go through blockHeap");
        // Flush path
        while (! blockHeap.isEmpty() && level >= 0) {
            // Get block at top of heap
            currentBlock = blockHeap.peek();
            TaoLogger.logForce("Looking at blockID " + currentBlock.getBlockID());
            // Find the path ID that this block maps to
            long pid = mPositionMap.getBlockPosition(currentBlock.getBlockID());

            TaoLogger.logForce("The path it is mapped to is " + pid);
            TaoLogger.logForce("The target path is " + pathID + " and the greatest common level is " + Utility.getGreatestCommonLevel(pathID, pid));

            // Check if this block can be inserted at this level
            if (Utility.getGreatestCommonLevel(pathID, pid) == level) {
                TaoLogger.logForce("We can insert blockID " + currentBlock.getBlockID() + " at level " + level);

                // If the block can be inserted at this level, get the bucket
                Bucket pathBucket = pathToFlush.getBucket(level);

                // Try to add this block into the path and update the bucket's timestamp
                if (pathBucket.addBlock(currentBlock, mWriteBackCounter)) {
                    TaoLogger.logForce("We have successfully inserted blockID " + currentBlock.getBlockID() + " at level " + level);
                    // If we have successfully added the block to the bucket, we remove the block from stash
                    mStash.removeBlock(currentBlock);

                    // Add new entry to subtree's map of block IDs to bucket
                    mSubtree.mapBlockToBucket(currentBlock.getBlockID(), pathBucket);

                    // If add was successful, remove block from heap and move on to next block without decrementing the
                    // level we are adding to
                    blockHeap.poll();
                    continue;
                }
            }

            TaoLogger.logForce("BlockID " + currentBlock.getBlockID() + " could not be inserted into level " + level);
            // If we are unable to add a block at this level, move on to next level
            level--;
        }

        // Add remaining blocks in heap to stash
        if (!blockHeap.isEmpty()) {
            while (!blockHeap.isEmpty()) {
                mStash.addBlock(blockHeap.poll());
            }
        }

        // mSubtree.addPath(decryptedPath, mWriteBackCounter);

        // Unlock the path
        pathToFlush.unlockPath();

        // Add this path to the write queue
        synchronized (mWriteQueue) {
            TaoLogger.logForce("Adding " + pathID + " to mWriteQueue");
            mWriteQueue.add(pathID);
        }
    }

    /**
     * @brief Method to create a max heap where the top element is the current block best suited to be placed along the path
     * @param pathID
     * @return max heap based on each block's path id when compared to the passed in pathID
     */
    public PriorityQueue<Block> getHeap(long pathID) {
        TaoLogger.log("! Trying to create heap");
        // Get all the blocks from the stash and blocks from this path
        ArrayList<Block> blocksToFlush = new ArrayList<>();
        blocksToFlush.addAll(mStash.getAllBlocks());
        Bucket[] buckets = mSubtree.getPath(pathID).getBuckets();
        for (Bucket b : buckets) {
            blocksToFlush.addAll(b.getFilledBlocks());
        }

        // Remove duplicates
        Set<Block> hs = new HashSet<>();
        hs.addAll(blocksToFlush);
        blocksToFlush.clear();
        blocksToFlush.addAll(hs);
        blocksToFlush = Lists.newArrayList(Sets.newHashSet(blocksToFlush));

        for (Block bl : blocksToFlush) {
            TaoLogger.logForce("in heap blockID " + bl.getBlockID());
        }

        // Create heap based on the block's path ID when compared to the target path ID
        PriorityQueue<Block> blockHeap = new PriorityQueue<>(TaoConfigs.BUCKET_SIZE, new BlockPathComparator(pathID, mPositionMap));
        blockHeap.addAll(blocksToFlush);
        TaoLogger.log("! Done making heap");
        return blockHeap;
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

        // Prune the mRequestMap to remove empty lists so it doesn't get to large
        mRequestMapLock.writeLock().lock();
        Set<Long> copy = new HashSet<>(mRequestMap.keySet());
        for (Long blockID : copy) {
            if (mRequestMap.get(blockID).isEmpty()) {
                mRequestMap.remove(blockID);
            }
        }
        mRequestMapLock.writeLock().unlock();
        try {
            // Create a map that will map each InetSockerAddress to a list of paths that will be written to it
            Map<InetSocketAddress, List<Long>> writebackMap = new HashMap<>();

            // Needed in order to clean up subtree later
            List<Long> allWriteBackIDs = new ArrayList<>();

            // Get the first TaoConfigs.WRITE_BACK_THRESHOLD path IDs from the mWriteQueue and place them in the map
            for (int i = 0; i < TaoConfigs.WRITE_BACK_THRESHOLD; i++) {
                // Get a path ID
                Long currentID;
                synchronized (mWriteQueue) {
                    currentID = mWriteQueue.remove();
                }
                TaoLogger.logForce("Writeback for path id " + currentID + " is mapped to ");

                // Check what server is responsible for this path
                InetSocketAddress isa = mPositionMap.getServerForPosition(currentID);

                // Add this path ID to the map
                List<Long> temp = writebackMap.get(isa);
                if (temp == null) {
                    temp = new ArrayList<>();
                    writebackMap.put(isa, temp);
                }
                temp.add(currentID);

                // Add to list of all the path IDs
                allWriteBackIDs.add(currentID);
            }

            // Current storage server we are targeting (corresponds to index into list of storage servers)
            int serverIndex = -1;

            // List of all the servers that successfully returned
            boolean[] serverDidReturn = new boolean[writebackMap.keySet().size()];

            // When a response is received from a server, this lock must be obtained to modify serverDidReturn
            Object returnLock = new Object();


            // Now we will send the writeback request to each server
            for (InetSocketAddress serverAddr : writebackMap.keySet()) {
                // Increment and save current server index
                serverIndex++;
                final int serverIndexFinal = serverIndex;

                // Get the list of paths to be written for the current server
                List<Long> writebackPaths = writebackMap.get(serverAddr);

                // Get all the encrypted path data
                byte[] dataToWrite = null;
                int pathSize = 0;

                // TODO: Should this be a snapshot writeback?
                for (int i = 0; i < writebackPaths.size(); i++) {
                    // Get path
                    Path p = mSubtree.getPath(writebackPaths.get(i));
                    if (p != null) {
                        // Set the path to correspond to the relative leaf ID as present on the server to be written to
                        p.setPathID(mRelativeLeafMapper.get(p.getPathID()));

                        // If this is the first path, don't need to concat the data
                        if (dataToWrite == null) {
                            dataToWrite = mCryptoUtil.encryptPath(p);
                            pathSize = dataToWrite.length;
                        } else {
                            dataToWrite = Bytes.concat(dataToWrite, mCryptoUtil.encryptPath(p));
                        }
                    }
                }
                TaoLogger.log("Going to do writeback");

                if (dataToWrite == null) {
                    serverDidReturn[serverIndexFinal] = true;
                    continue;
                }

                // Create the proxy write request
                ProxyRequest writebackRequest = mMessageCreator.createProxyRequest();
                writebackRequest.setType(MessageTypes.PROXY_WRITE_REQUEST);
                writebackRequest.setPathSize(pathSize);
                writebackRequest.setDataToWrite(dataToWrite);

                // Serialize the request
                byte[] encryptedWriteBackPaths = writebackRequest.serialize();
                if (encryptedWriteBackPaths == null) {
                    TaoLogger.log("encryptedWriteBackPaths is null");
                } else {
                    TaoLogger.log("encryptedWriteBackPaths is not null");
                }

                // Create and run server
                Runnable writebackRunnable = () -> {
                    try {
                        TaoLogger.logForce("Going to do writeback for server " + serverIndexFinal);
                        AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
                        Future connectionFuture = channel.connect(serverAddr);
                        connectionFuture.get();

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
                        TaoLogger.logForce("Sent info, now waiting to listen for server " + serverIndexFinal);
                        // Listen for server response
                        ByteBuffer messageTypeAndSize = MessageUtility.createTypeReceiveBuffer();
                        Future readHeader;
                        while (messageTypeAndSize.remaining() > 0) {
                            readHeader = channel.read(messageTypeAndSize);
                            readHeader.get();
                        }
                        // Flip the byte buffer for reading
                        messageTypeAndSize.flip();

                        // Parse the message type and size from server
                        int[] typeAndLength = MessageUtility.parseTypeAndLength(messageTypeAndSize);

                        int messageTypeInner = typeAndLength[0];
                        int messageLengthInner = typeAndLength[1];
                        messageTypeAndSize = null;
                        TaoLogger.logForce("Got info, going to do stuff now " + serverIndexFinal);
                        if (messageTypeInner == MessageTypes.SERVER_RESPONSE) {
                            // Read the response
                            ByteBuffer messageResponse = ByteBuffer.allocate(messageLengthInner);
                            Future readMessage;
                            while (messageResponse.remaining() > 0) {
                                readMessage = channel.read(messageResponse);
                                readMessage.get();
                            }
                            messageResponse.flip();

                            byte[] serialized = new byte[messageLengthInner];
                            messageResponse.get(serialized);
                            messageResponse = null;
                            // Create ServerResponse based on data
                            ServerResponse response = mMessageCreator.createServerResponse();
                            response.initFromSerialized(serialized);

                            // Check to see if the write succeeded or not
                            if (response.getWriteStatus()) {
                                // Acquire return lock
                                synchronized (returnLock) {
                                    // Set that this server did return
                                    serverDidReturn[serverIndexFinal] = true;

                                    // Check if all the servers have returned
                                    boolean allReturn = true;
                                    for (int n = 0; n < serverDidReturn.length; n++) {
                                        if (! serverDidReturn[n]) {
                                            allReturn = false;
                                            break;
                                        }
                                    }

                                    // If all the servers have successfully responded, we can delete nodes from subtree
                                    if (allReturn) {
                                        // Iterate through every path that was written, check if there are any nodes
                                        // we can delete
                                        for (Long pathID : allWriteBackIDs) {
                                            // Upon response, delete all nodes in subtree whose timestamp
                                            // is <= timeStamp, and are not in mPathReqMultiSet
                                            // TODO: check if shallow or deep copy
                                            Set<Long> set = new HashSet<>();
                                            for (Long l : mPathReqMultiSet.elementSet()) {
                                                set.add(l);
                                            }
                                            // mSubtree.printSubtree();
                                            mSubtree.deleteNodes(pathID, finalWriteBackTime, set);
                                        }

                                        TaoLogger.log("TREE AFTER WRITEBACK");
                                        mSubtree.printSubtree();
                                    }
                                }
                            } else {
                                // TODO: what happens on fail?
                            }

                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                };
                new Thread(writebackRunnable).start();
            }



//
//
//
//            // Now we will send the writeback request to each server
//            for (InetSocketAddress serverAddr : writebackMap.keySet()) {
//                // Increment and save current server index
//                serverIndex++;
//                final int serverIndexFinal = serverIndex;
//
//                // Get the list of paths to be written for the current server
//                List<Long> writebackPaths = writebackMap.get(serverAddr);
//
//                // Get all the encrypted path data
//                byte[] dataToWrite = null;
//                int pathSize = 0;
//
//                // TODO: Should this be a snapshot writeback?
//                for (int i = 0; i < writebackPaths.size(); i++) {
//                    // Get path
//                    Path p = mSubtree.getPath(writebackPaths.get(i));
//                    if (p != null) {
//                        // Set the path to correspond to the relative leaf ID as present on the server to be written to
//                        p.setPathID(mRelativeLeafMapper.get(p.getPathID()));
//
//                        // If this is the first path, don't need to concat the data
//                        if (dataToWrite == null) {
//                            dataToWrite = mCryptoUtil.encryptPath(p);
//                            pathSize = dataToWrite.length;
//                        } else {
//                            dataToWrite = Bytes.concat(dataToWrite, mCryptoUtil.encryptPath(p));
//                        }
//                    }
//                }
//                TaoLogger.log("Going to do writeback");
//
//                if (dataToWrite == null) {
//                    serverDidReturn[serverIndexFinal] = true;
//                    continue;
//                }
//
//                // Create the proxy write request
//                ProxyRequest writebackRequest = mMessageCreator.createProxyRequest();
//                writebackRequest.setType(MessageTypes.PROXY_WRITE_REQUEST);
//                writebackRequest.setPathSize(pathSize);
//                writebackRequest.setDataToWrite(dataToWrite);
//
//                // Serialize the request
//                byte[] encryptedWriteBackPaths = writebackRequest.serialize();
//                if (encryptedWriteBackPaths == null) {
//                    TaoLogger.log("encryptedWriteBackPaths is null");
//                } else {
//                    TaoLogger.log("encryptedWriteBackPaths is not null");
//                }
//
//                /* Write paths to server, wait for response */
//
//                // Open up channel to server
//                AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
//
//                // TODO: Use existing connection, but make sure writing many different times doesn't have negative impact
//                // Asynchronously connect to server
//                channel.connect(serverAddr, null, new CompletionHandler<Void, Void>() {
//                    @Override
//                    public void completed(Void result, Void attachment) {
//                        // First we send the message type to the server along with the size of the message
//                        ByteBuffer messageType = MessageUtility.createMessageHeaderBuffer(MessageTypes.PROXY_WRITE_REQUEST, encryptedWriteBackPaths.length);
//
//                        // Asynchronously write to server
//                        channel.write(messageType, null, new CompletionHandler<Integer, Void>() {
//                            @Override
//                            public void completed(Integer result, Void attachment) {
//                                // Now we send the rest of message to the server
//                                ByteBuffer message = ByteBuffer.wrap(encryptedWriteBackPaths);
//
//                                // Asynchronously write to server
//                                channel.write(message, null, new CompletionHandler<Integer, Void>() {
//                                    @Override
//                                    public void completed(Integer result, Void attachment) {
//                                        // TaoLogger.log("Sent the rest of the message for writeback");
//                                        if (message.remaining() > 0) {
//                                            channel.write(message, null, this);
//                                            return;
//                                        }
//
//                                        // Asynchronously read response type and size from server
//                                        ByteBuffer messageTypeAndSize = MessageUtility.createTypeReceiveBuffer();
//                                        channel.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {
//
//                                            @Override
//                                            public void completed(Integer result, Void attachment) {
//                                                // Flip the byte buffer for reading
//                                                messageTypeAndSize.flip();
//
//                                                // Parse the message type and size from server
//                                                int[] typeAndLength = MessageUtility.parseTypeAndLength(messageTypeAndSize);
//
//                                                int messageType = typeAndLength[0];
//                                                int messageLength = typeAndLength[1];
//
//                                                if (messageType == MessageTypes.SERVER_RESPONSE) {
//
//                                                    // Read the response
//                                                    ByteBuffer messageResponse = ByteBuffer.allocate(messageLength);
//
//                                                    channel.read(messageResponse, null, new CompletionHandler<Integer, Void>() {
//
//                                                        @Override
//                                                        public void completed(Integer result, Void attachment) {
//                                                            while (messageResponse.remaining() > 0) {
//                                                                channel.read(messageResponse);
//                                                            }
//
//                                                            messageResponse.flip();
//
//                                                            byte[] serialized = new byte[messageLength];
//                                                            messageResponse.get(serialized);
//
//                                                            // Create ServerResponse based on data
//                                                            ServerResponse response = mMessageCreator.createServerResponse();
//                                                            response.initFromSerialized(serialized);
//
//                                                            // Check to see if the write succeeded or not
//                                                            if (response.getWriteStatus()) {
//
//                                                                // Acquire return lock
//                                                                synchronized (returnLock) {
//                                                                    // Set that this server did return
//                                                                    serverDidReturn[serverIndexFinal] = true;
//
//                                                                    // Check if all the servers have returned
//                                                                    boolean allReturn = true;
//                                                                    for (int n = 0; n < serverDidReturn.length; n++) {
//                                                                        if (! serverDidReturn[n]) {
//                                                                            allReturn = false;
//                                                                            break;
//                                                                        }
//                                                                    }
//
//                                                                    // If all the servers have successfully responded, we can delete nodes from subtree
//                                                                    if (allReturn) {
//                                                                        // Iterate through every path that was written, check if there are any nodes
//                                                                        // we can delete
//                                                                        for (Long pathID : allWriteBackIDs) {
//                                                                            // Upon response, delete all nodes in subtree whose timestamp
//                                                                            // is <= timeStamp, and are not in mPathReqMultiSet
//                                                                            // TODO: check if shallow or deep copy
//                                                                            Set<Long> set = new HashSet<>();
//                                                                            for (Long l : mPathReqMultiSet.elementSet()) {
//                                                                                set.add(l);
//                                                                            }
//                                                                           // mSubtree.printSubtree();
//                                                                            mSubtree.deleteNodes(pathID, finalWriteBackTime, set);
//                                                                        }
//
//                                                                        TaoLogger.log("TREE AFTER WRITEBACK");
//                                                                       // mSubtree.printSubtree();
//                                                                    }
//                                                                }
//                                                            } else {
//                                                                // TODO: what happens on fail?
//                                                            }
//                                                        }
//
//                                                        @Override
//                                                        public void failed(Throwable exc, Void attachment) {
//                                                            // TODO: Implement?
//                                                        }
//                                                    });
//                                                }
//                                            }
//
//                                            @Override
//                                            public void failed(Throwable exc, Void attachment) {
//                                                // TODO: Implement?
//                                            }
//                                        });
//                                    }
//
//                                    @Override
//                                    public void failed(Throwable exc, Void attachment) {
//                                        // TODO: Implement?
//                                    }
//                                });
//                            }
//
//                            @Override
//                            public void failed(Throwable exc, Void attachment) {
//                                // TODO: Implement?
//                            }
//                        });
//                    }
//
//                    @Override
//                    public void failed(Throwable exc, Void attachment) {
//                        // TODO: Implement?
//                    }
//                });
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
