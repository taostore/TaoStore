package TaoProxy;

import com.sun.tools.internal.jxc.ap.Const;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @brief Class that represents the proxy which handles requests from clients and replies from servers
 */
public class TaoProxy implements Proxy {
    // Sequencer for proxy
    private Sequencer mSequencer;

    // Processor for proxy
    private Processor mProcessor;

    // The height of the ORAM tree stored on the server
    public static int TREE_HEIGHT;

    // The total amount of storage being outsourced to server
    public static long TOTAL_STORAGE_SIZE;

    /**
     * @brief Default constructor
     */
    public TaoProxy() {
        mSequencer = new TaoSequencer();
        mProcessor = new TaoProcessor();

        // Calculate the size of the ORAM tree in both height and total storage
        calculateSize(Constants.TOTAL_STORED_DATA);
    }

    /**
     * @brief Method that will calculate the height and total storage requirements for the ORAM tree based on
     * storageSize, which is the minimum amount of data, in MB, which most be available for storage
     * @param storageSize
     */
    public void calculateSize(long storageSize) {
        // Keep track of the storage size
        long totalTreeSize = storageSize;

        // Pad totalTreeSize so that we have a whole number of blocks
        if ((totalTreeSize % Constants.BLOCK_SIZE) != 0) {
            totalTreeSize += Constants.BLOCK_SIZE - (totalTreeSize % Constants.BLOCK_SIZE);
        }

        // Calculate how many blocks we currently have
        long numBlocks = totalTreeSize / Constants.BLOCK_SIZE;

        // Pad the number of blocks so we have a whole number of buckets
        if ((numBlocks % Constants.BUCKET_SIZE) != 0) {
            numBlocks += Constants.BUCKET_SIZE - (numBlocks % Constants.BUCKET_SIZE);
        }

        // Calculate the number of buckets we currently have
        long numBuckets = numBlocks / Constants.BUCKET_SIZE;

        // Calculate the height of our tree given the number of buckets we have
        TREE_HEIGHT = (int) Math.ceil((Math.log(numBuckets + 1) / Math.log(2)) - 1);

        // Given the height of tree, we now find the amount of buckets we need to make this a full binary tree
        numBuckets = (long) Math.pow(2, TREE_HEIGHT + 1) - 1;

        // We can now calculate the total size of the system
        totalTreeSize = numBuckets * Constants.BUCKET_SIZE * Constants.BLOCK_SIZE;
        TOTAL_STORAGE_SIZE = totalTreeSize;
    }

    @Override
    public void onReceiveRequest(Request req) {
        // TODO: Finish
        mSequencer.onReceiveRequest(req);
        mProcessor.readPath(req);
    }

    @Override
    public void onReceiveResponse(Response resp) {
        // TODO: lock bucket
        mProcessor.answerRequest(resp);

        // TODO: get data
        byte[] data = new byte[1];
        mSequencer.onReceiveResponse(resp, data);
        mProcessor.flush(resp.getPath().getID());

        // TODO: unlock bucket

        mProcessor.writeBack(1);
    }


    public void run() {
        try {
            // TODO: Properly configure to listen for messages from client and server
            // NOTE: currently code is just copy and pasted from internet

            // Create a thread pool for asynchronous sockets
            AsynchronousChannelGroup threadGroup =
                    AsynchronousChannelGroup.withFixedThreadPool(Constants.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());

            // Create a channel
            AsynchronousServerSocketChannel channel =
                    AsynchronousServerSocketChannel.open(threadGroup).bind(new InetSocketAddress(5000));

            channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                @Override
                public void completed(AsynchronousSocketChannel ch, Void att){
                    channel.accept(null, this);
                    // TODO: Check what information is in the channel ch, then take the appropriate action

                    // if ch is a request from client:
                        // parse the request
                        // onReceiveRequest(req)
                    // if ch is a response from server
                        // parse the response
                        // path = decryption of the returned path
                        // onReceiveResponse(rep)
                }

                @Override
                public void failed(Throwable exc, Void att) {
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        // Create proxy and run
        TaoProxy proxy = new TaoProxy();
        proxy.run();
    }
}
