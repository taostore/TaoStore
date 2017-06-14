package Heartbeats.Coordinator;
import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import Heartbeats.Messages.*;

/**
 * The Heartbeat Coordinator listens for connections
 * to it by Nodes. Each node updates its status
 * using its connection to the Heartbeat Coordinator.
 */
public class HeartbeatCoordinator implements Coordinator {

    private int heartbeatMillis;

	// Used to allow state access by only one thread at a time
	private final transient ReentrantLock mutexLock = new ReentrantLock();

    // Map of address to node status
    private Map<InetAddress, NodeStatus> nodes;

    // Map of address to channel
    private Map<InetAddress, SocketChannel> channels;

    // Heartbeat counter
    private AtomicLong heartbeat;

    // Timestamp of when the last heartbeat was sent
    private long lastHeartbeatTime;

    public HeartbeatCoordinator(List<InetAddress> addrs, int heartbeatMillis) {
        this.heartbeatMillis = heartbeatMillis;
        heartbeat = new AtomicLong(1);
        nodes = new HashMap<>();
        channels = new HashMap<>();
        System.out.println("Heartbeat coordinator tracking hosts:");
        for (InetAddress addr : addrs) {
            System.out.println(addr);
            nodes.put(addr, new NodeStatus(addr, false));
        }
    }

    public boolean isAvailable(InetAddress addr) {
        NodeStatus node = nodes.get(addr);
        if (node != null) {
            return node.isAvailable();
        } else {
            return false;
        }
    }

    private void sendHeartbeats() {

		// Copy out the list of addresses being tracked at the start of the round
		HashSet<InetAddress> addresses = new HashSet<>(nodes.keySet());
		
        for (InetAddress addr : addresses) {
			// Take the mutex to ensure that no modifications are made to nodes
			// or channels while checking this node
			mutexLock.lock();
            System.out.println("requester taking mutex");

            // Check if the last heartbeat was responded to
            if (!nodes.containsKey(addr)) {
                mutexLock.unlock();
                System.out.println("requester releasing mutex");
                continue;
            }

            NodeStatus node = nodes.get(addr);
            if (!(node.getLastResponse() == heartbeat.get())) {

                if (node.isAvailable()) {
                    node.setAvailable(false);
                    System.out.println("[" + System.currentTimeMillis() + "] Node at " + addr + " is not available");
                }
            }

            // Send the new heartbeat
            if (!channels.containsKey(addr)) {
                mutexLock.unlock();
                System.out.println("requester releasing mutex");
                continue;
            }

            HeartbeatRequestMessage hrm = new HeartbeatRequestMessage(heartbeat.get()+1);
            SocketChannel channel = channels.get(addr);
            byte[] message = hrm.serialize();

            try {
                channel.write(ByteBuffer.wrap(message));
            } catch (ClosedChannelException cce) {
                channels.remove(addr);
            } catch (IOException e) {
                // Do something?
            }

			// Release the mutex
			mutexLock.unlock();
            System.out.println("requester releasing mutex");
        }

        heartbeat.getAndIncrement();
        lastHeartbeatTime = System.currentTimeMillis();
    }

    private void requestThread() {
        // Continuously sleep and then request heartbeats on
        // open channels.
        while (true) {
            long elapsedMillis = System.currentTimeMillis() - lastHeartbeatTime;
            long remainingMillis = heartbeatMillis - elapsedMillis;
            if (remainingMillis <= 0) {
                sendHeartbeats();
            } else {
                try {
                    Thread.sleep(Math.min(remainingMillis, 3));
                } catch (InterruptedException e) {
                }
            }
        }
    }

    private void server() {

        try {

            // Set up server socket to accept connections and messages
            Selector selector = Selector.open();

            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress("127.0.0.1", 5005));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Heartbeat coordinator serving on port 5005");


            while (true) {
                try {
                    selector.select();
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();

                        keys.remove();

                        if (!key.isValid())
                            continue;
		
						// Take the mutex to avoid messing up variables
						// processed by the request thread
						mutexLock.lock();
						System.out.println("server taking mutex");
						
                        if (key.isAcceptable()) {
                            SocketChannel channel = serverChannel.accept();
                            channel.configureBlocking(false);
                            channel.register(selector, SelectionKey.OP_READ);
                            InetAddress remoteAddr = channel.socket().getInetAddress();
                            if (!nodes.containsKey(remoteAddr)) {
                                mutexLock.unlock();
                                System.out.println("server releasing mutex");
                                continue;
                            }
                            channels.put(remoteAddr, channel);
                            System.out.println("[" + System.currentTimeMillis() + "] Added channel from " + remoteAddr);
                        } else if (key.isReadable()) {
                            SocketChannel channel = (SocketChannel) key.channel();
                            InetAddress remoteAddr = channel.socket().getInetAddress();
                            if (!nodes.containsKey(remoteAddr)) {
                                mutexLock.unlock();
                                System.out.println("server releasing mutex");
                                continue;
                            }
                            NodeStatus node = nodes.get(remoteAddr);
                            ByteBuffer buffer = ByteBuffer.allocate(128);
                            int bytesRead = channel.read(buffer);

                            if (bytesRead < 1) {
                                channel.close();
                                key.cancel();
                            } else {
                                byte[] serialized = new byte[bytesRead];
                                System.arraycopy(buffer.array(), 0, serialized, 0, bytesRead);

                                HeartbeatResponseMessage hrm = new HeartbeatResponseMessage();
                                hrm.initFromSerialized(serialized);

                                if (hrm.getHeartbeat() == heartbeat.get()) {
                                    if (!node.isAvailable()) {
                                        node.setAvailable(true);
                                        System.out.println(System.currentTimeMillis() + " :: Node at " + remoteAddr + " is available");
                                    }
                                }
                                node.setLastResponse(hrm.getHeartbeat());
                            }
                        } 

						// Release the mutex
						mutexLock.unlock();
                        System.out.println("server releasing mutex");
                    }
                } catch (IOException e) {
                    // Do something?
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        System.out.println("Starting Hearbeat Coordinator");
        Runnable requestProcedure = () -> requestThread();
        new Thread(requestProcedure).start();
        server();
    }

}
