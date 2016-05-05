package TaoProxy;

/**
 * @brief
 */
public interface Proxy {
    /**
     * @brief Method to handle the receiving of a request from the client
     * @param request
     */
    void onReceiveRequest(ClientRequest request);

    /**
     * @brief
     * @param request
     * @param response
     * @param isFakeRead
     */
    void onReceiveResponse(ClientRequest request, ServerResponse response, boolean isFakeRead);

    void notifySequencer(ClientRequest req, ServerResponse resp, byte[] data);

    /**
     * @brief Method to run proxy indefinitely
     */
    void run();
}
