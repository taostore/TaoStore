package TaoProxyTest;

import org.junit.Test;

/**
 * Created by ajmagat on 5/4/16.
 */
public class ProxyRequestTest {
    @Test
    public void testSerialize() {
//        // Create a request to send to server
//        long pathID = 101;
//        ProxyRequest proxyRequest = new ProxyRequest(ProxyRequest.READ, pathID);
//
//        byte[] serializeReadRequest = proxyRequest.serialize();
//
//        ProxyRequest newProxyRequest = new ProxyRequest(serializeReadRequest);
//
//        assertEquals(pathID, newProxyRequest.getPathID());
//
//        ArrayList<Path> writeBackList = new ArrayList();
//        for (int i = 0; i < Constants.WRITE_BACK_THRESHOLD; i++) {
//            Path newPath = new Path(i);
//            writeBackList.add(newPath);
//        }
//
//        ProxyRequest proxyWriteRequest = new ProxyRequest(ProxyRequest.WRITE, writeBackList);
//
//        byte[] serializeWriteRequest = proxyWriteRequest.serialize();
//        ProxyRequest newProxyWriteRequest = new ProxyRequest(serializeWriteRequest);
//
//        List<Path> pathList = newProxyWriteRequest.getPathList();
//
//        int i = 0;
//        for (Path p : pathList) {
//            assertEquals(i, p.getPathID());
//            i++;
//        }

    }
}