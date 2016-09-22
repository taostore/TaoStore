package Messages;

/**
 * @brief Interface that represents a request from a proxy to the server
 */
public interface ProxyRequest {
    /**
     * @brief Initialize ProxyRequest based on a serialized version
     * @param serialized
     */
    void initFromSerialized(byte[] serialized);

    /**
     * @brief Get the data the proxy wants to write to server
     * @return the data that proxy wants to write to server
     */
    byte[] getDataToWrite();

    /**
     * @brief Set data to write to server
     * @param data
     */
    void setDataToWrite(byte[] data);

    /**
     * @brief Get the corresponding path ID for this proxy request, if it is a read request
     * @return a path ID
     */
    long getPathID();

    /**
     * @briefSet the path ID
     * @param pathID
     */
    void setPathID(long pathID);

    /**
     * @brief Get the size of a path for thie proxy request
     * @return path size
     */
    int getPathSize();

    /**
     * @brief Set the path size
     * @param pathSize
     */
    void setPathSize(int pathSize);

    /**
     * @brief Get the type of proxy request this is (read, write)
     * @return the type of proxy request this is
     */
    int getType();

    /**
     * @brief Set the type of this proxy request
     * @param type
     */
    void setType(int type);

    /**
     * @brief Method to serialize ProxyRequest into bytes
     * @return byte representation of ProxyRequest
     */
    byte[] serialize();
}
