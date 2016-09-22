package TaoProxy;

import Messages.ClientRequest;
import Messages.ServerResponse;

/**
 * @brief Interface for a TaoStore proxy
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

    /**
     * @brief Method to run proxy indefinitely
     */
    void run();
}
