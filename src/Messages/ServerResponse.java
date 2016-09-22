package Messages;

/**
 * @brief Interface to represent a response for server or proxy
 */
public interface ServerResponse {
    /**
     * @brief Initialize ServerResponse based on a serialized version
     * @param serialized
     */
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Get the path ID this server response is answering for (if responding to a read)
     * @return path ID
     */
    long getPathID();

    /**
     * @brief Set path ID
     * @param pathID
     */
    void setPathID(long pathID);

    /**
     * @brief Get the path associated with this response in bytes
     * @return path in bytes
     */
    byte[] getPathBytes();

    /**
     * @brief Set the bytes of a path
     * @param pathBytes
     */
    void setPathBytes(byte[] pathBytes);

    /**
     * @brief Get if this resposne is in response to a write
     * @return true or false depending on is response is in response to a write request
     */
    boolean getWriteStatus();
    
    /**
     * @brief Set if response is for a write request
     * @param status
     */
    void setIsWrite(boolean status);

    /**
     * @brief Method to serialize ServerResponse into bytes
     * @return byte representation of ServerResponse
     */
    byte[] serialize();
}
