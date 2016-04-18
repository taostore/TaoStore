package TaoProxy;

/**
 * @brief
 */
public interface Proxy {
    /**
     * @brief Method to handle the receiving of a request from the client
     * @param request
     */
    void onReceiveRequest(Request request);

    /**
     * @brief
     * @param response
     */
    void onReceiveResponse(Response response);

    /**
     * @brief Method to run proxy indefinitely
     */
    void run();
}
