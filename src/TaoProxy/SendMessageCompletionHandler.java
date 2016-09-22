package TaoProxy;

import com.google.common.primitives.Ints;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * TODO: Ideally use something like this in order to shorten code
 */
public class SendMessageCompletionHandler implements CompletionHandler<Void, Void> {

    private AsynchronousSocketChannel mChannel;
    private int mMessageType;
    private CompletionHandler<Integer, Void> mCompletetionHandler;

    public SendMessageCompletionHandler() {

    }

    public SendMessageCompletionHandler(AsynchronousSocketChannel channel, int messageType, CompletionHandler<Integer, Void> completetionHandler) {
        mChannel = channel;
        mMessageType = messageType;
        mCompletetionHandler = completetionHandler;
    }

    @Override
    public void completed(Void result, Void attachment) {
        byte[] messageType = Ints.toByteArray(mMessageType);
        mChannel.write(ByteBuffer.wrap(messageType), null, mCompletetionHandler);
    }

    @Override
    public void failed(Throwable exc, Void attachment) {
        // TODO: Implement?
    }
}
