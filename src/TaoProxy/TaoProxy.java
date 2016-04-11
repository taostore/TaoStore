package TaoProxy;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

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

    /**
     * @brief Method to run proxy indefinitely
     */
    public void run() {
        try {
            // TODO: Properly configure to listen for messages from client and server
            // NOTE: currently code is just copy and pasted from internet
            final AsynchronousServerSocketChannel listener = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(5000));
            listener.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
                @Override
                public void completed(AsynchronousSocketChannel ch, Void att) {
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
