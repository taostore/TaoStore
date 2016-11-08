package TaoProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @brief Implementation of a class that implements the Stash interface, which holds blocks for proxy
 */
public class TaoStash implements Stash {
    public ConcurrentMap<Long, Block> mStash;

    /**
     * @brief Default constructor
     */
    public TaoStash() {
        mStash = new ConcurrentHashMap<>();
    }

    @Override
    public List<Block> getAllBlocks() {
        ArrayList<Block> allBlocks = new ArrayList<>();
        for (Long key : mStash.keySet()) {
            allBlocks.add(mStash.get(key));
        }

        return allBlocks;
    }

    public void printKeySet() {
        for (Long key : mStash.keySet()) {
            TaoLogger.logForce("Block ID: " + key);
        }
    }

    @Override
    public void addBlock(Block b) {
        mStash.put(b.getBlockID(), b);
    }

    @Override
    public Block getBlock(long blockID) {
        return mStash.getOrDefault(blockID, null);
    }

    @Override
    public void removeBlock(Block b) {
        if (mStash.containsKey(b.getBlockID())) {
            mStash.remove(b.getBlockID());
        }
    }
}
