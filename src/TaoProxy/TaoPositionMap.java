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
     * @brief A constructor that will assign each leaf to one of the servers in storageServerAddresses
     * @param storageServerAddresses
     */
    public TaoPositionMap(List<InetSocketAddress> storageServerAddresses) {
        mPositions = new ConcurrentHashMap<>();
        mPartitionAddressMap = new ConcurrentHashMap<>();

        // Save number of servers
        int numServers = storageServerAddresses.size();

        // Get the number of leaves each server will contain
        int numLeaves = 1 << TaoConfigs.TREE_HEIGHT;
        int leavesPerServer = numLeaves / numServers;

        // Assign each leaf to a server
        int currentServer = 0;
        for (int i = 0; i < numLeaves; i += leavesPerServer) {
            long currentServerLeaves = i;

            // Add to the mPartitionAddressMap
            while (currentServerLeaves < i + leavesPerServer) {
                mPartitionAddressMap.put(currentServerLeaves, storageServerAddresses.get(currentServer));
                currentServerLeaves++;
            }

            // Increment the current server
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
