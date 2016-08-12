package TaoProxyTest;

import org.junit.Test;

/**
 * @brief
 */
public class ClientRequestTest {
    @Test
    public void testSerialize() {
//        long blockID = 573;
//        long requestID = 47;
//        InetSocketAddress address = new InetSocketAddress("localhost", 3760);
//        ClientRequest readRequest = new ClientRequest(blockID, ClientRequest.READ, requestID, address);
//
//        byte[] serializedRead = readRequest.serialize();
//        ClientRequest newReadRequest = new ClientRequest(serializedRead);
//
//        assertEquals(blockID, newReadRequest.getBlockID());
//        assertEquals(ClientRequest.READ, newReadRequest.getType());
//        assertEquals(requestID, newReadRequest.getRequestID());
//        assertEquals(address.getHostName(), newReadRequest.getClientAddress().getHostName());
//        assertEquals(address.getPort(), newReadRequest.getClientAddress().getPort());
//
//        blockID = 574;
//        requestID = 48;
//        address = new InetSocketAddress("localhost", 3761);
//        byte[] data = new byte[Constants.BLOCK_SIZE];
//        Arrays.fill( data, (byte) 3 );
//        ClientRequest writeRequest = new ClientRequest(blockID, ClientRequest.WRITE, requestID, data, address);
//
//        byte[] serializedWrite = writeRequest.serialize();
//        ClientRequest newWriteRequest = new ClientRequest(serializedWrite);
//
//        assertEquals(blockID, newWriteRequest.getBlockID());
//        assertEquals(ClientRequest.WRITE, newWriteRequest.getType());
//        assertEquals(requestID, newWriteRequest.getRequestID());
//        assertEquals(address.getHostName(), newWriteRequest.getClientAddress().getHostName());
//        assertEquals(address.getPort(), newWriteRequest.getClientAddress().getPort());
//        assertTrue(Arrays.equals(data, newWriteRequest.getData()));
    }
}