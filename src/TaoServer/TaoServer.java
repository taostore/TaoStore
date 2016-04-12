package TaoServer;

import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * @brief Class to represent a server for TaoStore
 */
public class TaoServer {
    /**
     * @brief Default constructor
     */
    public TaoServer() {

    }

    /**
     * @brief Method to run proxy indefinitely
     */
    public void run() {
        try {
            // TODO: Properly configure to listen for messages from proxy
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

    }
}
