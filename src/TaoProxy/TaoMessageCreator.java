package TaoProxy;

import Messages.*;

/**
 * @brief Implementation of a class that implements the MessageCreator interface
 *        Used to create different message types, possibly from a given serialization of a message
 */
public class TaoMessageCreator implements MessageCreator {
    public TaoMessageCreator() {
    }

    @Override
    public ClientRequest parseClientRequestBytes(byte[] data) {
        TaoClientRequest r = new TaoClientRequest();
        r.initFromSerialized(data);
        return r;
    }

    @Override
    public ProxyRequest parseProxyRequestBytes(byte[] data) {
        TaoProxyRequest r = new TaoProxyRequest();
        r.initFromSerialized(data);
        return r;
    }

    @Override
    public ProxyResponse parseProxyResponseBytes(byte[] data) {
        TaoProxyResponse r = new TaoProxyResponse();
        r.initFromSerialized(data);
        return r;
    }

    @Override
    public ServerResponse parseServerResponseBytes(byte[] data) {
        TaoServerResponse r = new TaoServerResponse();
        r.initFromSerialized(data);
        return r;
    }

    @Override
    public ClientRequest createClientRequest() {
        TaoClientRequest r = new TaoClientRequest();
        return r;
    }

    @Override
    public ProxyRequest createProxyRequest() {
        TaoProxyRequest r = new TaoProxyRequest();
        return r;
    }

    @Override
    public ProxyResponse createProxyResponse() {
        TaoProxyResponse r = new TaoProxyResponse();
        return r;
    }

    @Override
    public ServerResponse createServerResponse() {
        TaoServerResponse r = new TaoServerResponse();
        return r;
    }
}
