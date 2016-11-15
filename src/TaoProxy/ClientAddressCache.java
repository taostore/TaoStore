package TaoProxy;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @brief Class used to cache InetSocketAddresses so we do not need to create them each time
 */
public class ClientAddressCache {
    // Map from client hostname to InetSocketAddress for that client
    private static Map<String, InetSocketAddress> mCache = new ConcurrentHashMap<>();

    /**
     * @brief Get the InetSocketAddress for hostname, or if not present make one and insert it into map
     * @param hostname
     * @param port
     * @return InetSocketAddress for this hostname
     */
    public static InetSocketAddress getFromCache(String hostname, String port) {
        // Get address from cache
        InetSocketAddress addr = mCache.get(hostname + port);

        // If null, create InetSocketAddress and put it into cache
        if (addr == null) {
            addr = new InetSocketAddress(hostname, Integer.parseInt(port));
            mCache.put(hostname + port, addr);
        }

        // Return addr
        return addr;
    }
}
