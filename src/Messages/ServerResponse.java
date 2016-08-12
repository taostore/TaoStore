package Messages;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * @brief Class to represent a response for server or proxy
 */
public interface ServerResponse {
    void initFromSerialized(byte[] serialized);

    long getPathID();
    void setPathID(long pathID);

    byte[] serialize();

    byte[] getPathBytes();
    void setPathBytes(byte[] pathBytes);

    void setIsWrite(boolean status);
    boolean getWriteStatus();
}
