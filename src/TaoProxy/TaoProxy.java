package TaoProxy;

import com.google.common.primitives.Ints;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.Executors;

/**
 * @brief Class that represents the proxy which handles requests from clients and replies from servers
 */
public class TaoProxy implements Proxy {
    // Sequencer for proxy
    private Sequencer mSequencer;

    // Processor for proxy
    private Processor mProcessor;

    private AsynchronousChannelGroup mThreadGroup;

    // The height of the ORAM tree stored on the server
    public static int TREE_HEIGHT;

    // The total amount of storage being outsourced to server
    public static long TOTAL_STORAGE_SIZE;

    /**
     * @brief Default constructor
     */
    public TaoProxy(long minServerSize) {
        // Create a thread pool for asynchronous sockets
        try {
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(Constants.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());
            mSequencer = new TaoSequencer();
            mProcessor = new TaoProcessor(this, mThreadGroup);

            // Calculate the size of the ORAM tree in both height and total storage
            calculateSize(minServerSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    public void onReceiveRequest(ClientRequest req) {
        mSequencer.onReceiveRequest(req);
        mProcessor.readPath(req);
    }

    @Override
    public void onReceiveResponse(ClientRequest req, ServerResponse resp, boolean isFakeRead) {
        mProcessor.answerRequest(req, resp, isFakeRead);

        mProcessor.flush(resp.getPath().getID());

        mProcessor.writeBack(Constants.WRITE_BACK_THRESHOLD);
    }

    @Override
    public void notifySequencer(ClientRequest req, ServerResponse resp, byte[] data) {
        mSequencer.onReceiveResponse(req, resp, data);
    }

    public void run() {
        try {
            // Create a channel
            AsynchronousServerSocketChannel channel =
                    AsynchronousServerSocketChannel.open(mThreadGroup).bind(new InetSocketAddress(12344));

            // Wait for incoming connections
            channel.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                @Override
                public void completed(AsynchronousSocketChannel ch, Void att) {
                    // Start listening for another connection
                    channel.accept(null, this);

                    // Create a ByteBuffer to read in message
                    ByteBuffer byteBuffer = ByteBuffer.allocate(4);
                    ch.read(byteBuffer, null, new CompletionHandler<Integer, Void>() {
                        @Override
                        public void completed(Integer result, Void attachment) {
                            byte[] messageTypeBytes = new byte[4];
                            byteBuffer.flip();
                            byteBuffer.get(messageTypeBytes);

                            // TODO: decryption of messageTypeBytes

                            int messageType = Ints.fromByteArray(messageTypeBytes);

                            if (messageType == Constants.CLIENT_REQUEST) {
                                ByteBuffer messageSizeByteBuffer = ByteBuffer.allocate(4);
                                ch.read(messageSizeByteBuffer, null, new CompletionHandler<Integer, Void>() {

                                    @Override
                                    public void completed(Integer result, Void attachment) {
                                        // TODO: decryption of messageSizeBytes
                                        byte[] messageSizeBytes = new byte[4];
                                        messageSizeByteBuffer.flip();
                                        messageSizeByteBuffer.get(messageSizeBytes);
                                        int messageSize = Ints.fromByteArray(messageSizeBytes);
                                        // Parse the request

                                        ByteBuffer messageByteBuffer = ByteBuffer.allocate(messageSize);

                                        ch.read(messageByteBuffer, null, new CompletionHandler<Integer, Void>() {

                                            @Override
                                            public void completed(Integer result, Void attachment) {
                                                // TODO: decryption of requestBytes
                                                byte[] requestBytes = new byte[messageSize];
                                                messageByteBuffer.flip();
                                                messageByteBuffer.get(requestBytes);
                                                ClientRequest clientReq = new ClientRequest(requestBytes);

                                                // Handle request
                                                onReceiveRequest(clientReq);
                                            }

                                            @Override
                                            public void failed(Throwable exc, Void attachment) {

                                            }
                                        });

                                    }

                                    @Override
                                    public void failed(Throwable exc, Void attachment) {

                                    }
                                });
                            }
                        }

                        @Override
                        public void failed(Throwable exc, Void attachment) {

                        }
                    });
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
        TaoProxy proxy = new TaoProxy(Long.parseLong(args[0]));
        proxy.run();
    }
}
