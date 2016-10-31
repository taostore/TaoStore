package TaoProxy;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ajmagat on 10/28/16.
 */
public class ClientAddressCache {
    private static Map<String, InetSocketAddress> mCache = new ConcurrentHashMap<>();

    public static InetSocketAddress getFromCache(String hostname, String port) {
        InetSocketAddress addr = mCache.get(hostname + port);
        if (addr == null) {
            addr = new InetSocketAddress(hostname, Integer.parseInt(port));
            mCache.put(hostname + port, addr);
        }
        return addr;
    }
}
