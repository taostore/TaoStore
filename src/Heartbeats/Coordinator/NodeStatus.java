package Heartbeats.Coordinator;
import java.net.*;

public class NodeStatus {

    private InetAddress addr;
    private boolean available;
    private long lastResponse;

    public NodeStatus(InetAddress addr, boolean available) {
        this.addr = addr;
        this.available = available;
    }

    public void setAddress(InetAddress addr) {
        this.addr = addr;
    }

    public InetAddress getAddress() {
        return addr;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setLastResponse(long heartbeat) {
        this.lastResponse = heartbeat;
    }

    public long getLastResponse() {
        return lastResponse;
    }
}
