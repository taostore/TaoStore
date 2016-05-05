package TaoProxy;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TaoProcessor implements Processor {

    // Secret encryption key for blocks
    private SecretKey mKey;

    // Stash to hold blocks
    private TaoStash mStash;

    // Map that maps a block ID to a list of requests for that block ID
    private Map<Long, List<ClientRequest>> mRequestMap;

    // Map that maps block IDs to a ResponseMapEntry, signifying whether or not a request has been received or not
    private Map<ClientRequest, ResponseMapEntry> mResponseMap;

    // MultiSet which keeps track of which paths have been requested but not yet returned
    // This is needed for write back, to know what should or should not be deleted from the subtree when write completes
    private Multiset mPathReqMultiSet;

    // Subtree
    private Subtree mSubtree;

    // Counter until write back
    private long mWriteBackCounter;

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

        // Create queue of paths to be written
        mWriteQueue = new ConcurrentLinkedQueue<>();

        // Create position map
        mPositionMap = new TaoPositionMap();
    }

    @Override
    public void readPath(ClientRequest req) {
        try {
            System.out.println("Beginning readPath Processor");
            // Create new entry into response map
            mResponseMap.put(req, new ResponseMapEntry());

            // Check if this current block ID has other previous requests
            boolean fakeRead;
            long pathID;
            if (mRequestMap.get(req.getBlockID()) == null) {
                // If no other requests for this block ID have been made, it is not a fake read
                fakeRead = false;

                // In addition, find the path that this block maps to
                pathID = mPositionMap.getBlockPosition(req.getBlockID());

                // Insert request into request map
                mRequestMap.put(req.getBlockID(), Collections.singletonList(req));
            } else {
                fakeRead = true;

                // TODO: make this a randomly generated number
                Random r = new Random();
                pathID = r.nextInt(1 << TaoProxy.TREE_HEIGHT);

                // Append this request to the list of requests for this block ID
                mRequestMap.get(req.getBlockID()).add(req);
            }

            // Insert request into mPathReqMultiSet
            mPathReqMultiSet.add(pathID);

            // Open up channel to server
            // TODO: make way of changing server address
            AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(mThreadGroup);
            InetSocketAddress hostAddress = new InetSocketAddress("localhost", 12345);
            channel.connect(hostAddress, null, new CompletionHandler<Void, Void>() {
                @Override
                public void completed(Void result, Void attachment) {
                    // Create a request to send to server
                    ProxyRequest proxyRequest = new ProxyRequest(ProxyRequest.READ, pathID);
                    System.out.println("Processor sent out a request of type " + proxyRequest.getType());

                    // Serialize and encrypt request
                    // TODO: Encrypt request, or maybe not
                    byte[] requestData = proxyRequest.serializeAsMessage();

                    // Send message to server
                    channel.write(ByteBuffer.wrap(requestData), null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            // Asynchronously read response from server
                            System.out.println("sent to server");
                            ByteBuffer pathInBytes = ByteBuffer.allocate(4 + ServerResponse.getServerResponseSize());
                            channel.read(pathInBytes, null, new CompletionHandler<Integer, Void>() {
                                @Override
                                public void completed(Integer result, Void attachment) {
                                    // Parse response from server
                                    byte[] messageTypeBytes = new byte[4];

                                    pathInBytes.flip();
                                    pathInBytes.get(messageTypeBytes, 0, messageTypeBytes.length);

                                    // TODO: decryption of messageTypeBytes

                                    int messageType = Ints.fromByteArray(messageTypeBytes);

                                    if (messageType == Constants.SERVER_RESPONSE) {
                                        byte[] responseData = new byte[ServerResponse.getServerResponseSize()];
                                        pathInBytes.get(responseData);

                                        ServerResponse response = new ServerResponse(responseData);

                                        // When response comes back, remove one instance of the requested block ID from mPathReqMultiSet
                                        mPathReqMultiSet.remove(response.getPath().getID());

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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void answerRequest(ClientRequest req, ServerResponse resp, boolean isFakeRead) {
        // Get information about response
        boolean fakeRead = isFakeRead;

        // Insert every bucket along path that is not in subtree into subtree
        mSubtree.addPath(resp.getPath());

        // Update the response map entry for this request
        ResponseMapEntry responseMapEntry = mResponseMap.get(req);
        responseMapEntry.setReturned(true);

        // check if the data for this response entry is not null, which
        // would be the case if the real read returned before this fake read
        if (responseMapEntry.getData() != null) {
            // TODO: return mResponseMap.get(req).getData() to sequencer
            mProxy.notifySequencer(req, resp, responseMapEntry.getData());
            mResponseMap.remove(req);
            // return?
        }

        // Get a list of all the requests that have requested this block ID
        List<ClientRequest> requestList = new ArrayList<>(mRequestMap.get(req.getBlockID()));

        // Check to see if this is the request that caused the real read for this block
        if (!fakeRead) {
            // Loop through each request in list of requests for this block
            while (!requestList.isEmpty()) {
                // Get current request that will be processed
                ClientRequest currentRequest = requestList.remove(0);

                // Now we get the data from the desired block
                byte[] foundData = null;

                // First, from the path, find the bucket that has a block with blockID == req.getBlockID()
                Bucket targetBucket = mSubtree.getBucketWithBlock(currentRequest.getBlockID());

                // Check if such a targetBucket was in the subtree. If it was not, the block must be in the stash
                if (targetBucket == null) {
                    // The block we are looking for is not in any of the subtree buckets, so get it from the stash
                    Block targetBlock = mStash.getBlock(currentRequest.getBlockID());

                    // Check if the block is null, if so then block with this blockID does not yet exist
                    if (targetBlock != null) {
                        foundData = targetBlock.getData();
                    } else {
                        foundData = new byte[Constants.BLOCK_SIZE];
                    }
                } else {
                    // The block was found in one of the subtree buckets
                    foundData = targetBucket.getDataFromBlock(currentRequest.getBlockID());
                }

                // Check if the request was a write
                if (currentRequest.getType() == ClientRequest.WRITE) {
                    // Set the data for the block
                    if (targetBucket == null) {
                        // TODO modify stash entry
                        Block targetBlock = mStash.getBlock(currentRequest.getBlockID());
                        if (targetBlock != null) {
                            targetBlock.setData(currentRequest.getData());
                        } else {
                            Block newBlock = new Block(currentRequest.getBlockID());
                            newBlock.setData(currentRequest.getData());
                            mStash.addBlock(newBlock);
                            mPositionMap.setBlockPosition(currentRequest.getBlockID(), resp.getPath().getID());
                        }
                        //targetBlock.setData(currentRequest.getData());
                    } else {
                        targetBucket.modifyBlock(currentRequest.getBlockID(), currentRequest.getData());
                    }
                }

                // Check if the server has responded to this request yet
                if (mResponseMap.get(currentRequest).getRetured()) {
                    // TODO: send foundData to "sequencer"
                    mProxy.notifySequencer(req, resp, foundData);
                    mResponseMap.remove(currentRequest);
                } else {
                    // The server has not yet responded, so we just set the data for this response map entry and move on
                    responseMapEntry.setData(foundData);
                }
            }
        }

        // TODO: remove here or empty?
        mRequestMap.remove(req.getBlockID());

        if (!fakeRead) {
            // TODO: Assign block with blockID == req.getBlockID() to a new random path
            Random r = new Random();
            int newPathID = r.nextInt(1 << TaoProxy.TREE_HEIGHT);
            mPositionMap.setBlockPosition(req.getBlockID(), newPathID);
        }
    }

    @Override
    public void flush(long pathID) {
        // Increment the amount of times we have flushed
        mWriteBackCounter++;

        // Get path that will be flushed
        // TODO: don't clear, just push down
        Path pathToFlush = mSubtree.getPath(pathID);

        // Lock the path
        pathToFlush.lockPath();

        // Get a heap based on the block's path ID when compared to the target path ID
        PriorityQueue<Block> blockHeap = getHeap(pathID);

        // Clear each bucket
        // TODO: consider changing to not clear bucket
        for (int i = 0; i < pathToFlush.getPathHeight(); i++) {
            pathToFlush.getBucket(i).clearBucket();
        }

        // Variables to help with flushing path
        Block currentBlock;
        int level = TaoProxy.TREE_HEIGHT;

        // Flush path
        while (!blockHeap.isEmpty() && level >= 0) {
            // Get block at top of heap
            currentBlock = blockHeap.peek();

            // Find the path ID that this block maps to
            long pid = mPositionMap.getBlockPosition(currentBlock.getBlockID());

            // Check if this block can be inserted at this level
            if (Utility.getGreatestCommonLevel(pathID, pid) == level) {
                // If the block can be inserted at this level, get the bucket
                Bucket pathBucket = pathToFlush.getBucket(level);

                // Try to add this block into the path and update the bucket's timestamp
                if (pathBucket.addBlock(currentBlock, mWriteBackCounter)) {
                    mSubtree.mapBlockToBucket(currentBlock.getBlockID(), pathBucket);
                    // If add was successful, remove block from heap and move on to next block
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
        // Get all the blocks from the stash and blocks from this path
        ArrayList<Block> blocksToFlush = new ArrayList<>();

        blocksToFlush.addAll(mStash.getAllBlocks());
        Bucket[] buckets = mSubtree.getPath(pathID).getBuckets();
        for (Bucket b : buckets) {
            blocksToFlush.addAll(Arrays.asList(b.getBlocks()));
        }

        // Remove duplicates
        blocksToFlush = Lists.newArrayList(Sets.newHashSet(blocksToFlush));

        // Create heap based on the block's path ID when compared to the target path ID
        PriorityQueue<Block> blockHeap = new PriorityQueue<>(Constants.BUCKET_SIZE, new BlockPathComparator(pathID, mPositionMap));
        blockHeap.addAll(blocksToFlush);

        return blockHeap;
    }


    @Override
    public void writeBack(long timeStamp) {
        if (mWriteBackCounter % Constants.WRITE_BACK_THRESHOLD != 0) {
            return;
        }

        // Pop off pathIDs from write queue and retrieve corresponding paths from subtree
        ArrayList<Path> writebackPaths = new ArrayList<>();

        for(int i = 0; i < Constants.WRITE_BACK_THRESHOLD; i++) {
            writebackPaths.add(mSubtree.getPath(mWriteQueue.remove()));
        }

        // TODO: serialize all paths and encrypt
        //byte[] serializedPath = writebackPaths
        // TODO: write paths to server, wait for response
        // TODO: upon response, delete all nodes in subtree whose timestamp is <= timeStamp, and are not in mPathReqMultiSet
    }
}
