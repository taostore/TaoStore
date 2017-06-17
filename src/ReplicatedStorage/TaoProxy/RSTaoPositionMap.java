package ReplicatedStorage.TaoProxy;

import Configuration.TaoConfigs;
import TaoProxy.TaoPositionMap;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RSTaoPositionMap extends TaoPositionMap {
    // Map that maps each leafID to the address of a partition
    public ConcurrentMap<Long, List<InetSocketAddress>> mRSPartitionAddressMap;


    /**
     * @brief A constructor that will assign each leaf to one of the servers in each replica in storageServerAddresses
     * @param storageServerAddresses
     */
    public RSTaoPositionMap(Map<Integer, List<InetSocketAddress>> storageServerAddresses) {

        System.out.println("Storage Server addresses = {");
        int replica_num = 1;
        for (List<InetSocketAddress> replica : storageServerAddresses.values()) {
            for (InetSocketAddress sa : replica) {
                System.out.println(replica_num + ": " + sa);
            }
            replica_num += 1;
        }

        mPositions = new ConcurrentHashMap<>();
        mPartitionAddressMap = new ConcurrentHashMap<>();
        mRSPartitionAddressMap = new ConcurrentHashMap<>();

        // Save number of servers
        int numServers = 1; // TODO: fix (get number of patitions in each replica)

        // Get the number of leaves each server will contain
        int numLeaves = 1 << TaoConfigs.TREE_HEIGHT;
        int leavesPerServer = numLeaves / numServers;

        // Assign each leaf to a server
        int currentServer = 0;
        for (int i = 0; i < numLeaves; i += leavesPerServer) {
            long currentServerLeaves = i;

            // Add to the mRSPartitionAddressMap
            while (currentServerLeaves < i + leavesPerServer) {

                ArrayList<InetSocketAddress> servers = new ArrayList<>();
                for (List<InetSocketAddress> replica : storageServerAddresses.values()) {
                    servers.add(replica.get(currentServer));
                }

                mRSPartitionAddressMap.put(currentServerLeaves, servers);
                currentServerLeaves++;
            }

            // Increment the current server
            currentServer++;
        }
    }

    public List<InetSocketAddress> getServersForPosition(long leafID) {
        return mRSPartitionAddressMap.getOrDefault(leafID, null);
    }
}
