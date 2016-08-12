package TaoProxy;

import java.util.List;

/**
 * Created by ajmagat on 6/26/16.
 */
public interface Stash {
    /**
     * @brief Method to get all the blocks from stash
     * @return list of all blocks in stash
     */
    List<Block> getAllBlocks();

    /**
     * @brief Method to add block b to stash
     * @param b
     */
    void addBlock(Block b);

    Block getBlock(long blockID);

    void removeBlock(Block b);
}
