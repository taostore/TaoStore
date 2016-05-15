package TaoProxyTest;

import TaoProxy.*;

import java.nio.channels.AsynchronousChannelGroup;
import java.util.concurrent.Executors;

/**
 * @brief
 */
public class TestProxy implements Proxy {
    private Processor mProcessor;
    private AsynchronousChannelGroup mThreadGroup;
    private boolean mResponseReceived;
    private Path mReceivedPath;
    private Object mWaitLock;


    public TestProxy() {
        try {
            mThreadGroup = AsynchronousChannelGroup.withFixedThreadPool(Constants.PROXY_THREAD_COUNT, Executors.defaultThreadFactory());
            mProcessor = new TaoProcessor(this, mThreadGroup);
            mResponseReceived = false;
            mWaitLock = new Object();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onReceiveRequest(ClientRequest request) {

    }

    @Override
    public void onReceiveResponse(ClientRequest request, ServerResponse response, boolean isFakeRead) {
//        mResponseReceived = true;
//        System.out.println("onReceiveResponse test proxy " + request.getRequestID());
//
//        mReceivedPath = response.getPath();
//
//        synchronized (mWaitLock) {
//            mWaitLock.notifyAll();
//        }
    }

    @Override
    public void notifySequencer(ClientRequest req, ServerResponse resp, byte[] data) {

    }

    @Override
    public void run() {

    }

    public void setProcessor(Processor processor) {
    }

    public Processor getProcessor() {
        return mProcessor;
    }

    public boolean getResponseReceived() {
        return mResponseReceived;
    }

    public Path getReceivedPath() {
        return mReceivedPath;
    }

    public void waitOnProxy() {
        try {
            synchronized (mWaitLock) {
                mWaitLock.wait();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
