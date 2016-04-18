package TaoProxy;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TaoProcessor implements Processor {

    // Secret encryption key for blocks
    private SecretKey mKey;

    // Stash to hold blocks
    private TaoStash mStash;

    // Map that maps a block ID a list of requests for that block ID
    private Map<Long, List<Request>> mRequestMap;

    // Map that maps block IDs to a ResponseMapEntry, signifying whether or not a request has been received or not
    private Map<Request, ResponseMapEntry> mResponseMap;

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

    /**
     * @brief Default constructor
     */
    public TaoProcessor() {
        // TODO: Create secret key
        mKey = null;

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
    public void readPath(Request req) {
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
            pathID = 0;

            // Append this request to the list of requests for this block ID
            mRequestMap.get(req.getBlockID()).add(req);
        }

        // Insert request into mPathReqMultiSet
        mPathReqMultiSet.add(req.getBlockID());

        // TODO: Request path from server async request
    }

    @Override
    public void answerRequest(Response resp) {
        // When response comes back, remove one instance of the requested block ID from mPathReqMultiSet
        mPathReqMultiSet.remove(resp.getReq().getBlockID());

        // Get information about response
        Path path = resp.getPath();
        Request req = resp.getReq();
        boolean fakeRead = resp.isFakeRead();

        // Insert every bucket along path that is not in subtree into subtree
        mSubtree.addPath(resp.getPath());

        // Update the response map entry for this request
        ResponseMapEntry responseMapEntry = mResponseMap.get(req);
        responseMapEntry.setReturned(true);

        // check if the data for this response entry is not null, which
        // would be the case if the real read returned before this fake read
        if (responseMapEntry.getData() != null) {
            // TODO: return mResponseMap.get(req).getData() to sequencer
            mResponseMap.remove(req);
            // return?
        }

        // Get a list of all the requests that have requested this block ID
        List<Request> requestList = mRequestMap.get(req.getBlockID());

        // Check to see if this is the request that caused the real read for this block
        if (!fakeRead) {
            // Loop through each request in list of requests for this block
            while (!requestList.isEmpty()) {
                // Get current request that will be processed
                Request currentRequest = requestList.remove(0);

                // From the path, find the block with blockID == req.getBlockID() and get its data
                Bucket targetBucket = mSubtree.getBucketWithBlock(currentRequest.getBlockID());
                byte[] foundData = targetBucket.getBlock(currentRequest.getBlockID()).getData();

                // Check if the request was a write
                if (currentRequest.getType() == Request.RequestType.WRITE) {
                    targetBucket.modifyBlock(currentRequest.getBlockID(), currentRequest.getData());
                }

                if (mResponseMap.get(currentRequest).getRetured()) {
                    // TODO: send foundData to "sequencer"
                    mResponseMap.remove(currentRequest);
                } else {
                    responseMapEntry.setData(foundData);
                }
            }
        }

        if (!fakeRead) {
            // TODO: Assign block with blockID == req.getBlockID() to a new random path
            mPositionMap.setBlockPosition(req.getBlockID(), -1);
        }
    }

    @Override
    public void flush(long pathID) {
        // Increment the amount of times we have flushed
        mWriteBackCounter++;

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

        Block currentBlock;
        int level = TaoProxy.TREE_HEIGHT;
        Path newPath = new Path(pathID);

        while (!blockHeap.isEmpty() && level >= 0) {
            // Get block at top of heap
            currentBlock = blockHeap.peek();

            // Find the path ID that this block maps to
            long pid = mPositionMap.getBlockPosition(currentBlock.getBlockID());

            // Check if this block can be inserted at this level
            if (Utility.getGreatestCommonLevel(pathID, pid) == level) {
                // If the block can be inserted at this level, get the bucket
                Bucket pathBucket = newPath.getBucket(level);

                // Check to make sure bucket exists first
                if (pathBucket == null) {
                    // Insert empty bucket into level
                    newPath.insertBucket(null, level);
                    pathBucket = newPath.getBucket(level);
                }

                // Try to add this block into the path and update the bucket's timestamp
                if (pathBucket.addBlock(currentBlock, mWriteBackCounter)) {
                    blockHeap.poll();
                    continue;
                }
            }
            level--;
        }

        // Add this path to the write queue
        mWriteQueue.add(pathID);
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
        // TODO: write paths to server, wait for response
        // TODO: upon response, delete all nodes in subtree whose timestamp is <= timeStamp, and are not in mPathReqMultiSet
    }


}
