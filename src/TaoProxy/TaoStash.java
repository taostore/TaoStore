package TaoProxy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @brief Stash that holds blocks for proxy
 */
public class TaoStash {
    public ConcurrentMap<Long, Block> mStash;

    /**
     * @brief Default constructor
     */
    public TaoStash() {
        mStash = new ConcurrentHashMap<>();
    }


    /**
     * @brief Method to get all the blocks from stash
     * @return list of all blocks in stash
     */
    public List<Block> getAllBlocks() {
        ArrayList<Block> allBlocks = new ArrayList<>();
        System.out.println("blocks in stash are");
        for (Long key : mStash.keySet()) {
            System.out.println("block : " + key);
            allBlocks.add(mStash.get(key));
        }

        return allBlocks;
    }

    /**
     * @brief Method to add block b to stash
     * @param b
     */
    public void addBlock(Block b) {
        mStash.put(b.getBlockID(), b);
    }

    public Block getBlock(long blockID) {
        return mStash.getOrDefault(blockID, null);
    }

    public void removeBlock(Block b) {
        if (mStash.containsKey(b.getBlockID())) {
            mStash.remove(b.getBlockID());
        }
    }
}
