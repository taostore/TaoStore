package TaoProxy;

import java.net.InetSocketAddress;

/**
 * Created by ajmagat on 6/26/16.
 */
public interface PositionMap {
    /**
     * @brief Method to map a block ID to a leaf ID
     * @param blockID
     * @param leafID
     */
    void setBlockPosition(long blockID, long leafID);

    /**
     * @brief Method to retrieve the leaf ID corresponding to a give block ID
     * @param blockID
     * @return the leaf ID for given block ID, or -1 if block ID is not currently in position map
     */
    long getBlockPosition(long blockID);

    /**
     *
     * @param leafID
     * @return
     */
    InetSocketAddress getServerForPosition(long leafID);
}
