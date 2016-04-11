package TaoProxy;

import java.util.List;
import java.util.Map;

/**
 * @brief Class that represents the subtree component of the proxy
 */
public class TaoSubtree implements Subtree {
    // Map that maps a block ID to the bucket that contains that block
    private Map<Long, Bucket> mBlockMap;

    /**
     * @brief Default constructor
     */
    public TaoSubtree() {
    }

    @Override
    public void addPath(Path path) {

    }

    @Override
    public Path getPath(long pathID) {
        return null;
    }

    /**
     * @brief Method to get all blocks stored in subtree
     * @return all blocks in subtree
     */
    public List<Block> getAllBlocks() {
        // TODO: finish
        return null;
    }
}
