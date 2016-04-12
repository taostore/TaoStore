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

    public void run() {
        // Wait for request from Sequencer

        // When request is received, spin up new thread
            // In new thread:
                // pathID, path, fakeRead = readPath(request)
                // acquire locks for bucket that is being read/written
                // answerRequest(request, pathID, path, fakeRead)
                // flush(pathID)
                // unlock bucket

        // check if counter is a multiple of k, c * k
        // if so, call writeBack(c)
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

        // TODO: Request path from server

        // When response comes back, remove one instance of the requested block ID from mPathReqMultiSet
        mPathReqMultiSet.remove(req.getBlockID());

        // TODO: Decrypt path
        // path = decryption of the returned path

        // TODO: Return the pathID, the path, fakeRead (whether or not this is a fake read)
    }

    @Override
    public void answerRequest(Request req, int pathID, Path path, boolean fakeRead) {
        // insert every bucket W along path that is not in subtree into subtree
        mSubtree.addPath(path);

        // Update the response map entry for this request
        ResponseMapEntry responseMapEntry = mResponseMap.get(req);
        responseMapEntry.setReturned(true);

        // check if x is not null, which would be the case if the real read returned before this fake read
        if (responseMapEntry.getData() != null) {
            // TODO: return mResponseMap.get(req).getData() to sequencer
            mResponseMap.remove(req);
            // return?
        }

        List<Request> requestList = mRequestMap.get(req.getBlockID());
        if (!fakeRead) {
            while (!requestList.isEmpty()) {
                Request currentRequest = requestList.remove(0);

                // From the path, find the block with blockID == req.getBlockID() and get its data
                byte[] foundData = path.getData(req.getBlockID());


                // Check if the request was a write
                if (req.getType() == Request.RequestType.WRITE) {
                    // TODO: Set block with blockID == req.getBlockID() to have req.getData()
                }

                if (mResponseMap.get(req).getRetured()) {
                    // TODO: send foundData to "sequencer"
                    mResponseMap.remove(req);
                } else {
                    responseMapEntry.setData(foundData);
                }
            }
        }

        if (!fakeRead) {
            // TODO: Assign block with blockID == req.getBlockID() to a new random path
            mPositionMap.setBlockPosition(req.getBlockID(), -1);
        }

        // if fakeRead == false
            // while mRequestMap[req.mBlockID].size() != 0
                // currentRequest = mRequestMap[req.mBlockID].pop()
                // from the path, find block with block id req.mBlockID and get it's value Z
                // if req.type == write
                    // set block with req.mBlockID to have data req.data
                // if response.map(req) == true
                    // send Z to sequencer
                    // response.map.remove(req)
                // else
                    // response.map(req) = (false, Z)
    }

    @Override
    public void flush(long pathID) {
        ArrayList<Block> blocksToFlush = new ArrayList<>();
        blocksToFlush.addAll(mStash.getAllBlocks());
        blocksToFlush.addAll(mStash.getAllBlocks());
        blocksToFlush = Lists.newArrayList(Sets.newHashSet(blocksToFlush));;

        // TODO: Create a heap based on current blocks

        for (Block b : blocksToFlush) {
            // push block bid as far as possible in subtree down path pathID
        }

        mWriteBackCounter++;
        mWriteQueue.add(pathID);

        // TODO: for every bucket that has been updated, add time-stamp t = counter
    }

    @Override
    public void writeBack(long timeStamp) {
        // Pop off pathIDs from write queue and retrieve corresponding paths from subtree
        ArrayList<Path> writebackPaths = new ArrayList<>();

        for(int i = 0; i < Constants.WRITE_BACK_THRESHOLD; i++) {
            writebackPaths.add(mSubtree.getPath(mWriteQueue.remove()));
        }

        // copy k paths to temporary space S
        // encrypt paths in S
        // write paths in S to server with timestamp timeStamp
        // when server responds:
            // delete buckets in subtree from the k paths when the timestamp of that bucket is <= timeStamp * k and
            // the bucket is not in path on mPathReqMultiSet
    }
}
