package TaoProxy;

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
    private Map<Request, byte[]> mRequestMap;

    // Queue of the received requests
    private BlockingQueue<Request> mRequestQueue;

    /**
     * @brief Default constructor for the TaoStore Sequencer
     */
    public TaoSequencer() {
        // NOTE: ConcurrentHashMap is weakly consistent amongst different threads. Should be fine in this scenario
        mRequestMap = new ConcurrentHashMap<>();
        mRequestQueue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    }

    @Override
    public void onReceiveRequest(Request req) {
        mRequestMap.put(req, null);
        mRequestQueue.add(req);

        /* TODO: send request to processor, probably handle in proxy */
    }

    @Override
    public void onReceiveResponse(Response resp, byte[] data) {
        mRequestMap.replace(resp.getReq(), data);
    }

    @Override
    public void sendRequest(Request req) {

    }

    @Override
    public void sendResponse(Response resp) {

    }

    @Override
    public void serializationProcedure() {
        // Run forever
        while (true) {
            try {
                // Retrieve request from request queue
                // Blocks if there is no item in queue
                Request req = mRequestQueue.take();

                // Wait until the reply for req somes back
                while (mRequestMap.get(req) == null) {}

                // TODO: return mRequestMap.get(req) to client

                // Remove request from request map
                mRequestMap.remove(req);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
