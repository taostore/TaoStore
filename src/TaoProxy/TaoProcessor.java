package TaoProxy;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TaoProcessor implements Processor {

    // Secret encryption key for blocks
    private SecretKey mKey;

    // Stash to hold blocks
    private TaoStash mStash;

    // Map that maps a block ID to a list of requests for that block ID
    private Map<Long, List<ClientRequest>> mRequestMap;
    private final transient ReentrantReadWriteLock mRequestMapLock = new ReentrantReadWriteLock();

    // Map that maps client requests to a ResponseMapEntry, signifying whether or not a request has been received or not
    private Map<ClientRequest, ResponseMapEntry> mResponseMap;

    // MultiSet which keeps track of which paths have been requested but not yet returned
    // This is needed for write back, to know what should or should not be deleted from the subtree when write completes
    private Multiset<Long> mPathReqMultiSet;

    // Subtree
    private Subtree mSubtree;

    // Counter until write back
    private long mWriteBackCounter;

    //
    private long mNextWriteBack;

    private final transient ReentrantLock mWriteBackLock = new ReentrantLock();

    // Write Queue
    private Queue<Long> mWriteQueue;

    // Position map which keeps track of what leaf each block corresponds to
    private TaoPositionMap mPositionMap;

    // Proxy that this processor belongs to
    private Proxy mProxy;

    // The channel group used for asynchronous socket
    private AsynchronousChannelGroup mThreadGroup;

    /**
     * @brief Default constructor
     */
    public TaoProcessor(Proxy proxy, AsynchronousChannelGroup threadGroup) {
        mProxy = proxy;

        mThreadGroup = threadGroup;

        // TODO: Create secret key
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256); // for example
            SecretKey secretKey = keyGen.generateKey();
            mKey = null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Create stash
        mStash = new TaoStash();

        // Create request map
        mRequestMap = new HashMap<>();

        // Create response map
        mResponseMap = new ConcurrentHashMap<>();

        // Create requested path multiset
        mPathReqMultiSet = ConcurrentHashMultiset.create();

        // Create subtree
        mSubtree = new TaoSubtree();

        // Create counter the keep track of number of flushes
        mWriteBackCounter = 0;
        mNextWriteBack = Constants.WRITE_BACK_THRESHOLD;

        // Create queue of paths to be written
        mWriteQueue = new ConcurrentLinkedQueue<>();

        // Create position map
        mPositionMap = new TaoPositionMap();
    }

    @Override
    public void readPath(ClientRequest req) {
        try {
            System.out.println("--- Starting a readPath for " + req.getBlockID() + " and request id " + req.getRequestID());
            // Create new entry into response map
            mResponseMap.put(req, new ResponseMapEntry());

            // Check if this current block ID has other previous requests
            boolean fakeRead;
            long pathID;
            // Check if there is any current request for this block ID
            if (mRequestMap.get(req.getBlockID()) == null || mRequestMap.get(req.getBlockID()).isEmpty()) {
                System.out.println("This request list is empty for block ID " + req.getBlockID());
                // If no other requests for this block ID have been made, it is not a fake read
                fakeRead = false;

                // In addition, find the path that this block maps to
                // TODO: Make the default return -1 if the element does not yet exist, pass that down
                pathID = mPositionMap.getBlockPosition(req.getBlockID());
                if (pathID == -1) {
                    // TODO: should pathID now be assigned something random?
                    Random r = new Random();
                    pathID = r.nextInt(1 << TaoProxy.TREE_HEIGHT);
                }

                // Insert request into request map
                mRequestMapLock.readLock().lock();
                if (mRequestMap.get(req.getBlockID()) == null) {
                    ArrayList<ClientRequest> newList = new ArrayList<>();
                    newList.add(req);
                    mRequestMap.put(req.getBlockID(), newList);
                } else {
                    mRequestMap.get(req.getBlockID()).add(req);
                }
                mRequestMapLock.readLock().unlock();
            } else {
                System.out.println("The request list is not empty for block ID " + req.getBlockID() + " and has " + mRequestMap.get(req.getBlockID()).get(0).getRequestID());
                // There is currently a request for the block ID, so we need to trigger a fake read
                fakeRead = true;

                // TODO: make this a randomly generated number
                // Generate a random number to be used for path ID
                Random r = new Random();
                pathID = r.nextInt(1 << TaoProxy.TREE_HEIGHT);

                // Append this request to the list of requests for this block ID
                mRequestMapLock.readLock().lock();
                mRequestMap.get(req.getBlockID()).add(req);
                mRequestMapLock.readLock().unlock();
            }

            System.out.println("Doing a read for path ID: " + pathID);

            // Insert request into mPathReqMultiSet to make sure that this path is not deleted before this response
            // returns from server
            // TODO: Might need to move this to the end of answerRequest
            mPathReqMultiSet.add(pathID);

            // Open up channel to server
            // TODO: make way of changing server address
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
            InetSocketAddress hostAddress = new InetSocketAddress("localhost", 12345);

            // Asynchronously connect to server
            // Create effectively final variables to use for inner classes
            long finalPathID = pathID;
            channel.connect(hostAddress, null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    // Create a request to send to server
                    ProxyRequest proxyRequest = new ProxyRequest(ProxyRequest.READ, finalPathID);

                    // Serialize and encrypt request
                    // TODO: Encrypt request, or maybe not
                    byte[] requestData = proxyRequest.serialize();


                    // First we send the message type to the server along with the size of the message
                    byte[] messageTypeBytes = Ints.toByteArray(Constants.PROXY_READ_REQUEST);
                    byte[] messageLengthBytes = Ints.toByteArray(requestData.length);

                    ByteBuffer messageType = ByteBuffer.wrap(Bytes.concat(messageTypeBytes, messageLengthBytes));

                    // Asynchronously send message type and length to server
                    channel.write(messageType, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {

                            // Write the rest of the message to the server
                            System.out.println("Going to send proxy request to server, requestID " + req.getRequestID());

                            channel.write(ByteBuffer.wrap(requestData), null, new CompletionHandler<Integer, Void>() {

                                @Override
                                public void completed(Integer result, Void attachment) {
                                    System.out.println("Wrote proxy request to server, going to wait for response, requestID " + req.getRequestID());

                                    // Asynchronously read response type and size from server
                                    ByteBuffer messageTypeAndSize = ByteBuffer.allocate(4 + 4);

                                    channel.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {

                                        @Override
                                        public void completed(Integer result, Void attachment) {
                                            // Flip the byte buffer for reading
                                            messageTypeAndSize.flip();

                                            // Parse the message type and size from server
                                            byte[] messageTypeBytes = new byte[4];
                                            byte[] messageLengthBytes = new byte[4];

                                            messageTypeAndSize.get(messageTypeBytes);
                                            messageTypeAndSize.get(messageLengthBytes);

                                            int messageType = Ints.fromByteArray(messageTypeBytes);
                                            int messageLength = Ints.fromByteArray(messageLengthBytes);

                                            // Asynchronously read response from server
                                            ByteBuffer pathInBytes = ByteBuffer.allocate(messageLength);
                                            channel.read(pathInBytes, null, new CompletionHandler<Integer, Void>() {
                                                @Override
                                                public void completed(Integer result, Void attachment) {
                                                    System.out.println("Got response from server for proxy request, requestID " + req.getRequestID());
                                                    System.out.println("The initial limit is " + pathInBytes.limit());
                                                    System.out.println("How much is remaining " + pathInBytes.remaining());

                                                    // TODO: change all reads to be like this
                                                    while (pathInBytes.remaining() > 0) {
                                                        channel.read(pathInBytes, null, this);
                                                        return;
                                                    }
                                                    // Flip the byte buffer for reading
                                                    pathInBytes.flip();

                                                    //pathInBytes.position(0);
                                                    System.out.println("Limit after the flip is " + pathInBytes.limit());

                                                    // Serve message based on type
                                                    if (messageType == Constants.SERVER_RESPONSE) {
                                                        // Read in message bytes
                                                        byte[] responseData = new byte[ServerResponse.getServerResponseSize()];
                                                        System.out.println("Limit is " + pathInBytes.limit());
                                                        System.out.println("Size of response data is " + responseData.length);
                                                        pathInBytes.get(responseData);

                                                        // Create ServerResponse object based on data
                                                        ServerResponse response = new ServerResponse(responseData);

                                                        // Now that the response has come back, remove one instance of the requested
                                                        // block ID from mPathReqMultiSet
                        //                                mPathReqMultiSet.remove(response.getPath().getID());

                                                        // Send response to proxy
                                                        mProxy.onReceiveResponse(req, response, fakeRead);
                                                    }
                                                }

                                                @Override
                                                public void failed(Throwable exc, Void attachment) {
                                                    // TODO: Implement?
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
                        public void failed(Throwable exc, Void attachment) {
                            // TODO: Implement?
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    // TODO: Implement?
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void answerRequest(ClientRequest req, ServerResponse resp, boolean isFakeRead) {
        System.out.println("--- Going to answer request with requestID " + req.getRequestID());
        // Get information about response
        boolean fakeRead = isFakeRead;

        // Insert every bucket along path that is not in subtree into subtree
        mSubtree.addPath(resp.getPath());

        // Update the response map entry for this request
        ResponseMapEntry responseMapEntry = mResponseMap.get(req);
        responseMapEntry.setReturned(true);

        // Check if the data for this response entry is not null, which would be the case if the real read returned
        // before this fake read
        if (responseMapEntry.getData() != null) {
            System.out.println("The data was already set, going to notify sequencer for requestID " + req.getRequestID());
            // Send the data to the sequencer
            mProxy.notifySequencer(req, resp, responseMapEntry.getData());

            // Remove this request from the response map
            mResponseMap.remove(req);
            return;
        }

        System.out.println("The data was not set yet for the response");

        // Get a list of all the requests that have requested this block ID
        List<ClientRequest> requestList = mRequestMap.get(req.getBlockID());

        // Figure out if this is the first time the element has appeared
        boolean elementDoesExist = mPositionMap.getBlockPosition(req.getBlockID()) != -1;
        boolean isFirstRequest = true;
        // Check to see if this is the request that caused the real read for this block
        if (!fakeRead) {
            System.out.println("Request with ID " + req.getRequestID() + " and blockID " + req.getBlockID() + " is not a fake read");
            // Loop through each request in list of requests for this block
            while (!requestList.isEmpty()) {
                // Get current request that will be processed
                ClientRequest currentRequest = requestList.remove(0);

                // Now we get the data from the desired block
                byte[] foundData;


                // First, from the subtree, find the bucket that has a block with blockID == req.getBlockID()
                // TODO: call this, bucket is changed via flush, foundData. Need to lock bucket?
                // TODO: only for the first non fake read
                if (elementDoesExist || ! isFirstRequest) {
                    System.out.println("BlockID " + req.getBlockID() + " should exist somewhere");
                    // The element should exist somewhere
                    foundData = getDataFromBlock(currentRequest.getBlockID());
                } else {
                    System.out.println("BlockID " + req.getBlockID() + " does not yet exist");
                    // The element has never been created before
                    foundData = new byte[Constants.BLOCK_SIZE];
                }

                // Check if the request was a write
                if (currentRequest.getType() == ClientRequest.WRITE) {
                    System.out.println("The requestID " + req.getRequestID() + " was a write");

                    if (elementDoesExist || ! isFirstRequest) {
                        // The element should exist somewhere
                        writeDataToBlock(currentRequest.getBlockID(), currentRequest.getData());
                    } else {
                        Block newBlock = new Block(currentRequest.getBlockID());
                        newBlock.setData(currentRequest.getData());

                        // Add block to stash and assign random path position
                        mStash.addBlock(newBlock);
                    }
                }

                // Check if the server has responded to this request yet
                if (mResponseMap.get(currentRequest).getRetured()) {
                    // Send the data to sequencer
                    mProxy.notifySequencer(req, resp, foundData);

                    // Remove this request from the response map
                    mResponseMap.remove(currentRequest);
                } else {
                    // The server has not yet responded, so we just set the data for this response map entry and move on
                    responseMapEntry.setData(foundData);
                }

                isFirstRequest = false;
            }
        }

        if (!fakeRead) {
            // Assign block with blockID == req.getBlockID() to a new random path in position map
            Random r = new Random();
            int newPathID = r.nextInt(1 << TaoProxy.TREE_HEIGHT);
            System.out.println("%%%% Assigning blockID " + req.getBlockID() + " to path " + newPathID);
            mPositionMap.setBlockPosition(req.getBlockID(), newPathID);
        }

        // Now that the response has come back, remove one instance of the requested
        // block ID from mPathReqMultiSet
        mPathReqMultiSet.remove(resp.getPath().getID());
    }

    /**
     * @brief
     * @param blockID
     * @return
     */
    public byte[] getDataFromBlock(long blockID) {
        System.out.println("$$ Trying to get data for block " + blockID);
        while (true) {
            Bucket targetBucket = mSubtree.getBucketWithBlock(blockID);
            if (targetBucket != null) {
                System.out.println("Target bucket isn't null");
                byte[] data = targetBucket.getDataFromBlock(blockID);
                if (data != null) {
                    System.out.println("$$ Returning data for block " + blockID);
                    return data;
                } else {
                    System.out.println("But bucket does not have the data we want");
                    System.exit(1);
                }
            } else {
                System.out.println("Cannot find in subtree");
                Block targetBlock = mStash.getBlock(blockID);
                if (targetBlock != null) {
                    System.out.println("$$ Returning data for block " + blockID);
                    return targetBlock.getData();
                } else {
                    System.out.println("Cannot find in subtree or stash");
                    System.exit(0);
                }
            }
        }
    }

    public void writeDataToBlock(long blockID, byte[] data) {
        while (true) {
            Bucket targetBucket = mSubtree.getBucketWithBlock(blockID);
            if (targetBucket != null) {
                if (targetBucket.modifyBlock(blockID, data)) {
                    return;
                }
            } else {
                Block targetBlock = mStash.getBlock(blockID);
                if (targetBlock != null) {
                    targetBlock.setData(data);
                }
            }
        }
    }

    @Override
    public void flush(long pathID) {
        System.out.println("--- Doing a flush for pathID " + pathID);
        // Increment the amount of times we have flushed
        mWriteBackCounter++;

        // Get path that will be flushed
        // TODO: don't clear, just push down
        Path pathToFlush = mSubtree.getPath(pathID);

        System.out.println("Trying to lock entire path");
        // Lock every bucket on the path
        pathToFlush.lockPath();
        System.out.println("Got lock for path");
        // Get a heap based on the block's path ID when compared to the target path ID
        PriorityQueue<Block> blockHeap = getHeap(pathID);

        System.out.println("!!!! The blocks in the heap are: mWriteBackCount: " + mWriteBackCounter );
        System.out.println("? why so many lines");
        for (Block b : blockHeap) {
            System.out.println("Block : " + b.getBlockID());
//            for (byte by : b.getData()) {
//                System.out.print(by);
//            }
        //    System.out.println();
        }

        // Clear each bucket
        // TODO: consider changing to not clear bucket
        for (int i = 0; i < pathToFlush.getPathHeight(); i++) {
            pathToFlush.getBucket(i).clearBucket();
        }

        // Variables to help with flushing path
        Block currentBlock;
        int level = TaoProxy.TREE_HEIGHT;
        System.out.println("The tree height is " + level);
        // Flush path
        while (!blockHeap.isEmpty() && level >= 0) {
            // Get block at top of heap
            currentBlock = blockHeap.peek();

            // Find the path ID that this block maps to
            long pid = mPositionMap.getBlockPosition(currentBlock.getBlockID());
            System.out.println("pid is " + pid);

            // Check if this block can be inserted at this level
            if (Utility.getGreatestCommonLevel(pathID, pid) == level) {
                // If the block can be inserted at this level, get the bucket
                Bucket pathBucket = pathToFlush.getBucket(level);

                // Try to add this block into the path and update the bucket's timestamp
                // TODO: remove from stash
                if (pathBucket.addBlock(currentBlock, mWriteBackCounter)) {
                    mStash.removeBlock(currentBlock);
                    System.out.println("we were able to add blockID " + currentBlock.getBlockID() + " at level " + level + " for path " + pathID);
                    // Add new entry to subtree's map of block IDs to bucket
                    mSubtree.mapBlockToBucket(currentBlock.getBlockID(), pathBucket);

                    // If add was successful, remove block from heap and move on to next block without decrementing the
                    // level we are adding to
                    blockHeap.poll();
                    continue;
                }
            }

            // If we are unable to add a block at this level, move on to next level
            level--;
        }

        // Add remaining blocks in heap to stash
        if (!blockHeap.isEmpty()) {
            while (!blockHeap.isEmpty()) {
                mStash.addBlock(blockHeap.poll());
            }
        }

        // Unlock the path
        pathToFlush.unlockPath();

        // Add this path to the write queue
        mWriteQueue.add(pathID);
    }

    /**
     * @brief Method to create a max heap where the top element is the current block best suited to be placed along the path
     * @param pathID
     * @return max heap based on each block's path id when compared to the passed in pathID
     */
    public PriorityQueue<Block> getHeap(long pathID) {
        System.out.println("! Trying to create heap");
        // Get all the blocks from the stash and blocks from this path
        ArrayList<Block> blocksToFlush = new ArrayList<>();

        blocksToFlush.addAll(mStash.getAllBlocks());
        System.out.println("Getting the buckets in path " + pathID);
        Bucket[] buckets = mSubtree.getPath(pathID).getBuckets();
       // System.out.println("! The subtree had " + buckets.length + " buckets");
        for (Bucket b : buckets) {
            System.out.println("How many filled blocks? " + b.getFilledBlocks().size());
            blocksToFlush.addAll(b.getFilledBlocks());
        }

        System.out.println("Just added all the blocks to an arraylist");

        // Remove duplicates
        Set<Block> hs = new HashSet<>();
     //   hs.addAll(blocksToFlush);
       // blocksToFlush.clear();
        //blocksToFlush.addAll(hs);
        //blocksToFlush = Lists.newArrayList(Sets.newHashSet(blocksToFlush));

        System.out.println("Just removed all the duplicates, size is " + blocksToFlush.size());
        // Create heap based on the block's path ID when compared to the target path ID
        PriorityQueue<Block> blockHeap = new PriorityQueue<>(Constants.BUCKET_SIZE, new BlockPathComparator(pathID, mPositionMap));
        blockHeap.addAll(blocksToFlush);

        System.out.println("Returning the heap of size " + blockHeap.size());
        return blockHeap;
    }


    @Override
    public void writeBack(long timeStamp) {
        // Check if we should trigger a write back
        // TODO: mWriteBackCounter can be incremented by multiple threads, in theory this check could never work

        long writeBackTime = 0;
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
                    mNextWriteBack += Constants.WRITE_BACK_THRESHOLD;

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


        System.out.println("## DOING THE WRITEBACK");

        // Save path IDs that we will be popping off
        long[] writePathIDs = new long[Constants.WRITE_BACK_THRESHOLD];

        // Pop off pathIDs from write queue and retrieve corresponding paths from subtree
        ArrayList<Path> writeBackPaths = new ArrayList<>();
        for(int i = 0; i < Constants.WRITE_BACK_THRESHOLD; i++) {
            Path p = mSubtree.getPath(mWriteQueue.remove());
            writeBackPaths.add(p);
            writePathIDs[i] = p.getID();
        }

        // TODO: encrypt
        ProxyRequest writebackRequest = new ProxyRequest(ProxyRequest.WRITE, writeBackPaths);
        byte[] encryptedWriteBackPaths = writebackRequest.serialize();

        try {
            // Write paths to server, wait for response
            // Open up channel to server
            // TODO: make way of changing server address
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
            InetSocketAddress hostAddress = new InetSocketAddress("localhost", 12345);

            // Asynchronously connect to server
            channel.connect(hostAddress, null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {

                    // First we send the message type to the server along with the size of the message
                    byte[] messageTypeBytes = Ints.toByteArray(Constants.PROXY_WRITE_REQUEST);
                    byte[] messageLengthBytes = Ints.toByteArray(encryptedWriteBackPaths.length);

                    ByteBuffer messageType = ByteBuffer.wrap(Bytes.concat(messageTypeBytes, messageLengthBytes));

                    // Asynchronously write to server
                    channel.write(messageType, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            // Now we send the rest of message to the server
                            ByteBuffer message = ByteBuffer.wrap(encryptedWriteBackPaths);

                            // Asynchronously write to server
                            channel.write(message, null, new CompletionHandler<Integer, Void>() {
                                @Override
                                public void completed(Integer result, Void attachment) {
                                    // Asynchronously read response type and size from server
                                    ByteBuffer messageTypeAndSize = ByteBuffer.allocate(4 + 4);

                                    channel.read(messageTypeAndSize, null, new CompletionHandler<Integer, Void>() {

                                        @Override
                                        public void completed(Integer result, Void attachment) {
                                            // Flip the byte buffer for reading
                                            messageTypeAndSize.flip();

                                            // Parse the message type and size from server
                                            byte[] messageTypeBytes = new byte[4];
                                            byte[] messageLengthBytes = new byte[4];

                                            messageTypeAndSize.get(messageTypeBytes);
                                            messageTypeAndSize.get(messageLengthBytes);

                                            int messageType = Ints.fromByteArray(messageTypeBytes);
                                            int messageLength = Ints.fromByteArray(messageLengthBytes);

                                            if (messageType == Constants.SERVER_RESPONSE) {
                                                ByteBuffer messageResponse = ByteBuffer.allocate(messageLength);
                                                channel.read(messageResponse, null, new CompletionHandler<Integer, Void>() {

                                                    @Override
                                                    public void completed(Integer result, Void attachment) {
                                                        while(messageResponse.remaining() > 0) {
                                                            channel.read(messageResponse);
                                                        }

                                                        messageResponse.flip();

                                                        byte[] responseData = new byte[messageLength];
                                                        messageResponse.get(responseData);

                                                        // Create ServerResponse based on data
                                                        ServerResponse response = new ServerResponse(responseData);

                                                        // Check to see if the write succeeded or not
                                                        if (response.getWriteStatus()) {
                                                            // Iterate through every path that was written, check if there are any nodes
                                                            // we can delete
                                                            for (Long pathID : writePathIDs) {
                                                                // Upon response, delete all nodes in subtree whose timestamp
                                                                // is <= timeStamp, and are not in mPathReqMultiSet
                                                                // TODO: check if shallow or deep copy
                                                                Set<Long> set = new HashSet<>();
                                                                for (Long l : mPathReqMultiSet.elementSet()) {
                                                                    set.add(l);
                                                                }
                                                                mSubtree.deleteNodes(pathID, finalWriteBackTime, set);
                                                            }
                                                        }
                                                    }

                                                    @Override
                                                    public void failed(Throwable exc, Void attachment) {

                                                    }
                                                });
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
                            // TODO: Implement?
                        }
                    });
                }

                @Override
                public void failed(Throwable exc, Void attachment) {
                    // TODO: Implement?
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
