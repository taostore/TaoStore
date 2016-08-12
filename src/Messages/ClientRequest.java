package Messages;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.sun.tools.javac.util.ArrayUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * @brief A client request that is of the form (type, block id, data)
 */
public interface ClientRequest {
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Accessor for mBlockID
     * @return the block ID for the requested block
     */
    long getBlockID();

    void setBlockID(long blockID);

    /**
     * @brief Accessor for mType
     * @return the type of request
     */
    int getType();

    void setType(int type);

    /**
     * @brief Accessor for mData
     * @return the data that was requested to be set on write (null if type is read)
     */
    byte[] getData();

    /**
     * @brief Mutator for mData
     * @param data
     */
    void setData(byte[] data);

    /**
     * @brief Function to serialize ClientRequest into bytes
     * @return byte representation of ClientRequest
     */
    byte[] serialize();

    /**
     * @brief Accessor for mRequestID
     * @return the unique ID for this request
     */
    long getRequestID();

    void setRequestID(long requestID);

    InetSocketAddress getClientAddress();

    void setClientAddress(InetSocketAddress clientAddress);
}
