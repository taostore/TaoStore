package Heartbeats.Coordinator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

public class HeartbeatCoordinatorTest {
    public static void main(String[] args) {
        ArrayList<InetAddress> addrs = new ArrayList<>();
        addrs.add(new InetSocketAddress("127.0.0.1", 5001).getAddress());
        HeartbeatCoordinator coordinator = new HeartbeatCoordinator(addrs, 500);
        coordinator.start();
    }
}
