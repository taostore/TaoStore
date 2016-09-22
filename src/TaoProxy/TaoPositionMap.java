package TaoProxy;

import Configuration.TaoConfigs;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @brief Implementation of a class that implements the PositionMap interface.
 */
public class TaoPositionMap implements PositionMap {
    // Map that maps each blockID to a leafID
    public ConcurrentMap<Long, Long> mPositions;

    // Map that maps each leafID to the address of a partition
    public ConcurrentMap<Long, InetSocketAddress> mPartitionAddressMap;

    /**
     * @brief Default constructor
     */
    public TaoPositionMap() {
        mPositions = new ConcurrentHashMap<>();
    }

    /**
     * @brief
     * @param storageServerAddresses
     * TODO: Currently number of servers have to be a power of 2
     */
    public TaoPositionMap(List<InetSocketAddress> storageServerAddresses) {
        mPositions = new ConcurrentHashMap<>();
        mPartitionAddressMap = new ConcurrentHashMap<>();
        int numServers = storageServerAddresses.size();

        // Check if power of two
        if ((numServers & -numServers) != numServers) {
            // TODO: only use a power of two for the servers, throw error?
        }

        // Get the number of leaves each server will contain
        int numLeaves = 1 << TaoConfigs.TREE_HEIGHT;
        int leavesPerServer = numLeaves / numServers;

        // Assign each leaf to a server
        int currentServer = 0;
        for (int i = 0; i < numLeaves; i += leavesPerServer) {
            long currentServerLeaves = i;
            while (currentServerLeaves < i + leavesPerServer) {
                TaoLogger.log("skeddit assigning " + currentServerLeaves + " to server " + storageServerAddresses.get(currentServer).getHostName());
                mPartitionAddressMap.put(currentServerLeaves, storageServerAddresses.get(currentServer));
                currentServerLeaves++;
            }
            currentServer++;
        }
    }

    @Override
    public void setBlockPosition(long blockID, long leafID) {
        mPositions.put(blockID, leafID);
    }

    @Override
    public long getBlockPosition(long blockID) {
        return mPositions.getOrDefault(blockID, -1L);
    }

    @Override
    public InetSocketAddress getServerForPosition(long leafID) {
        return mPartitionAddressMap.getOrDefault(leafID, null);
    }
}
