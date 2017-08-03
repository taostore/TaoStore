package TaoProxy;

import Messages.ClientRequest;

import java.net.InetSocketAddress;

public interface Profiler {
    void writeStatistics();

    void readPathStart(ClientRequest req);

    void readPathComplete(ClientRequest req);

    void writeBackStart(long writeBackTime);

    void writeBackComplete(long writeBackTime);

    void readPathServerProcessingTime(InetSocketAddress address, ClientRequest req, long processingTime);

    void writeBackServerProcessingTime(InetSocketAddress address, long writeBackTime, long processingTime);

    void readPathPreSend(InetSocketAddress address, ClientRequest req);

    void readPathPostRecv(InetSocketAddress address, ClientRequest req);

    void writeBackPreSend(InetSocketAddress address, long writeBackTime);

    void writeBackPostRecv(InetSocketAddress address, long writeBackTime);

    void addPathTime(long processingTime);
}
