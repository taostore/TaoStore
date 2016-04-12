package TaoProxy;

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
public class TaoProxy {
    // Sequencer for proxy
    private Sequencer mSequencer;

    // Processor for proxy
    private Processor mProcessor;

    /**
     * @brief Default constructor
     */
    public TaoProxy() {
        mSequencer = new TaoSequencer();
        mProcessor = new TaoProcessor();

    }

    /**
     * @brief Method to handle the receiving of a request from the client
     * @param req
     */
    public void onReceiveRequest(Request req) {
        // TODO: Finish
        mSequencer.onReceiveRequest(req);
        mProcessor.readPath(req);
    }

    public void onReceiveResponse(Response resp) {

    }

    /**
     * @brief Method to run proxy indefinitely
     */
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
