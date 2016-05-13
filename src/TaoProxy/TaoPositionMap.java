package TaoProxy;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TaoPositionMap {
    public ConcurrentMap<Long, Long> mPositions;

    /**
     * @brief Default constructor
     */
    public TaoPositionMap() {
        mPositions = new ConcurrentHashMap<>();
    }

    /**
     * @brief Method to map a block ID to a leaf ID
     * @param blockID
     * @param leafID
     */
    public void setBlockPosition(long blockID, long leafID) {
        mPositions.put(blockID, leafID);
    }

    /**
     * @brief Method to retrieve the leaf ID corresponding to a give block ID
     * @param blockID
     * @return the leaf ID for given block ID, or -1 if block ID is not currently in position map
     */
    public long getBlockPosition(long blockID) {
        return mPositions.getOrDefault(blockID, -1L);
    }
}
