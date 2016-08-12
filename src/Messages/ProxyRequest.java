package Messages;

/**
 * @brief Interface that represents a request from a proxy to the server
 */
public interface ProxyRequest {
    void initFromSerialized(byte[] serialized);

    byte[] getDataToWrite();
    void setDataToWrite(byte[] data);

    long getPathID();
    void setPathID(long pathID);

    int getPathSize();
    void setPathSize(int pathSize);

    int getType();
    void setType(int type);

    byte[] serialize();
}
