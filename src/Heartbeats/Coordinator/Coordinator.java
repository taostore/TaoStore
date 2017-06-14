package Heartbeats.Coordinator;

import java.net.InetAddress;

public interface Coordinator {
    public boolean isAvailable(InetAddress addr);
    public void start();
}