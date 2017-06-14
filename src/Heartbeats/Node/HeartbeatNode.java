package Heartbeats.Node;

import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import Heartbeats.Messages.*;

public class HeartbeatNode implements Node {

    public void start() {

        try {
            // Establish a channel with the Heartbeat Coordinator
            long heartbeat = -1;

            SocketChannel channel = null;
            while (channel == null) {
                try {
                    channel = SocketChannel.open(new InetSocketAddress("0.0.0.0", 5005));
                } catch (Exception e) {

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ie) {
                    }
                }
            }

            System.out.println("Connected to Heartbeat Coordinator at " + channel.getRemoteAddress());
            HeartbeatResponseMessage hrm = new HeartbeatResponseMessage(heartbeat);
            byte[] message = hrm.serialize();
            channel.write(ByteBuffer.wrap(message));


            // Continuously handle requests from the heartbeat coordinator
            while (true) {

                try {

                    // Read in a request from the channel
                    ByteBuffer buffer = ByteBuffer.allocate(128);
                    int bytesRead = channel.read(buffer);

                    if (bytesRead < 1)
                        continue;

                    byte[] serialized = new byte[bytesRead];
                    System.arraycopy(buffer.array(), 0, serialized, 0, bytesRead);

                    HeartbeatRequestMessage req = new HeartbeatRequestMessage();
                    req.initFromSerialized(serialized);
                    heartbeat = req.getHeartbeat();

                    // Respond to the request
                    HeartbeatResponseMessage resp = new HeartbeatResponseMessage(heartbeat);
                    byte[] messageBytes = resp.serialize();
                    channel.write(ByteBuffer.wrap(messageBytes));
                } catch (IOException e) {
                    // Do something?
                }

            }
        } catch (IOException e) {
            System.err.println("Failed to set up connection to Heartbeat Coordinator");
        }
    }

}