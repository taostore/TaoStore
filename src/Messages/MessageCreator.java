package Messages;

/**
 * @brief Interface for class that can create different message types
 */
public interface MessageCreator {
    /**
     * @brief Create a ClientRequest from a serialized version
     * @param data
     * @return an initialized ClientRequest
     */
    ClientRequest parseClientRequestBytes(byte[] data);

    /**
     * @brief Create a ProxyRequest from a serialized version
     * @param data
     * @return an initialized ProxyRequest
     */
    ProxyRequest parseProxyRequestBytes(byte[] data);

    /**
     * @brief Create a ProxyResponse from a serialized version
     * @param data
     * @return an initialized ProxyResponse
     */
    ProxyResponse parseProxyResponseBytes(byte[] data);

    /**
     * @brief Create a ServerResponse from a serialized version
     * @param data
     * @return an initialized ServerResponse
     */
    ServerResponse parseServerResponseBytes(byte[] data);

    /**
     * @brief Create an empty ClientRequest
     * @return an empty ClientRequest
     */
    ClientRequest createClientRequest();

    /**
     * @brief Create an empty ProxyRequest
     * @return an empty ProxyRequest
     */
    ProxyRequest createProxyRequest();

    /**
     * @brief Create an empty ProxyResponse
     * @return an empty ProxyResponse
     */
    ProxyResponse createProxyResponse();

    /**
     * @brief Create an empty ServerResponse
     * @return an empty ServerResponse
     */
    ServerResponse createServerResponse();
}
