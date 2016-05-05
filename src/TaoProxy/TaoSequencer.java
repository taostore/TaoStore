package TaoProxy;

import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @brief The Sequencer makes sure that replies are sent to the client in the same order that requests were received
 */
public class TaoSequencer implements Sequencer{
    // Size of request queue
    private final static int QUEUE_SIZE = 1000;

    // Map that will map each request to the value of the requested block
    // The value will be null if the reply to this request has not yet been received
    private Map<ClientRequest, Block> mRequestMap;

    // Queue of the received requests
    private BlockingQueue<ClientRequest> mRequestQueue;

    /**
     * @brief Default constructor for the TaoStore Sequencer
     */
    public TaoSequencer() {
        // NOTE: ConcurrentHashMap is weakly consistent amongst different threads. Should be fine in this scenario
        mRequestMap = new ConcurrentHashMap<>();
        mRequestQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);

        Runnable serializeProcedure = this::serializationProcedure;

        new Thread(serializeProcedure).start();
    }

    @Override
    public void onReceiveRequest(ClientRequest req) {
        Block empty = new Block(true);

        mRequestMap.put(req, empty);
        mRequestQueue.add(req);
    }

    @Override
    public void onReceiveResponse(ClientRequest req, ServerResponse resp, byte[] data) {
        Block b = new Block();
        //boolean s = data == null;
        //System.out.println("data is " + data.length);
        System.out.println("Does this exist " + mRequestMap.get(req).getBlockID());
        b.setData(data);
       // mRequestMap.get(req).setData(data);
        mRequestMap.replace(req, b);
    }

    @Override
    public void serializationProcedure() {
        // Run forever
        while (true) {
            try {
                System.out.println("Sequencer spinning");
                // Retrieve request from request queue
                // Blocks if there is no item in queue
                ClientRequest req = mRequestQueue.take();

                // Wait until the reply for req somes back
                // TODO: Change from spin
                byte[] check = null;
                while (check == null) {
                    check = mRequestMap.get(req).getData();
                }

                System.out.println("Sequencer going to send response");


                // Return mRequestMap.get(req) to client
                ProxyResponse response = null;

                if (req.getType() == ClientRequest.READ) {
                    response = new ProxyResponse(req.getRequestID(), mRequestMap.get(req).getData());
                } else if (req.getType() == ClientRequest.WRITE) {
                    response = new ProxyResponse(req.getRequestID(), true);
                }

                System.out.print("Sequencer says message ");
                for (byte b : response.serialize()) {
                    System.out.print(b);
                }
                System.out.println();
                InetSocketAddress address = req.getClientAddress();
                Socket socket = new Socket(address.getHostName(), address.getPort());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.write(response.serializeAsMessage());
                output.close();

                // Remove request from request map
                mRequestMap.remove(req);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
