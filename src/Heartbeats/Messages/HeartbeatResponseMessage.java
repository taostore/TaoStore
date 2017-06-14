package Heartbeats.Messages;

import java.util.Arrays;
import com.google.common.primitives.Longs;

public class HeartbeatResponseMessage {

    long heartbeat;

    public HeartbeatResponseMessage() {

    }

    public HeartbeatResponseMessage(long heartbeat) {
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
