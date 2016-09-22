package Messages;

import java.net.InetSocketAddress;

/**
 * @brief Interface the represents a request from the client to the proxy
 */
public interface ClientRequest {
    /**
     * @brief Initialize ClientRequest based on a serialized version
     * @param serialized
     */
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Get the block ID for the request block
     * @return the block ID for the requested block
     */
    long getBlockID();

    /**
     * @brief Set the block ID
     * @param blockID
     */
    void setBlockID(long blockID);

    /**
     * @brief Get the type of request
     * @return the type of request
     */
    int getType();

    /**
     * @brief Set the type of request
     * @param type
     */
    void setType(int type);

    /**
     * @brief Get the data that was requested to be written
     * @return the data that was requested to be set on write (null if type is read)
     */
    byte[] getData();

    /**
     * @brief Set the data to write
     * @param data
     */
    void setData(byte[] data);

    /**
     * @brief Get this request's unique ID
     * @return the unique ID for this request
     */
    long getRequestID();

    /**
     * @brief Set this request's unique ID
     * @param requestID
     */
    void setRequestID(long requestID);

    /**
     * @brief Get the client address for this request
     * @return the address where this request originated
     */
    InetSocketAddress getClientAddress();

    /**
     * @brief Set the client address for this request
     * @param clientAddress
     */
    void setClientAddress(InetSocketAddress clientAddress);

    /**
     * @brief Method to serialize ClientRequest into bytes
     * @return byte representation of ClientRequest
     */
    byte[] serialize();
}
