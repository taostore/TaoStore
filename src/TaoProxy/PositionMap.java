package TaoProxy;

import java.net.InetSocketAddress;

/**
 * Interface for position map
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
     * @brief Method to return the address of the server that contains the path corresponding to leaf id
     * @param leafID
     * @return an InetSocketAddress
     */
    InetSocketAddress getServerForPosition(long leafID);
}
