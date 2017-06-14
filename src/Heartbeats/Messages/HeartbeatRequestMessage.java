package Heartbeats.Messages;

import java.util.Arrays;
import com.google.common.primitives.Longs;

public class HeartbeatRequestMessage {

    long heartbeat;

    public HeartbeatRequestMessage() {

    }

    public HeartbeatRequestMessage(long heartbeat) {
        this.heartbeat = heartbeat;
    }

    public void initFromSerialized(byte[] serialized) {
        heartbeat = Longs.fromByteArray(Arrays.copyOfRange(serialized, 0, 8));
    }

    public byte[] serialize() {
        byte[] returnData = null;
        byte[] heartbeatBytes = Longs.toByteArray(heartbeat);
        returnData = heartbeatBytes;
        return returnData;
    }

    public long getHeartbeat() {
        return heartbeat;
    }

}
