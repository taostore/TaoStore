package TaoProxy;

import java.util.List;

/**
 * Interface for Stash
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

    /**
     * @brief Get the block with the corresponding block ID
     * @param blockID
     * @return block with a block id matching blockID
     */
    Block getBlock(long blockID);

    /**
     * @brief Remove block from stash
     * @param b
     */
    void removeBlock(Block b);
}
