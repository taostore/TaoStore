package TaoProxy;

import Messages.*;

/**
 * TODO: Create interface, possibly make this a class
 */
public class TaoMessageCreator implements MessageCreator {
    public TaoMessageCreator() {
    }

    public ClientRequest parseClientRequestBytes(byte[] data) {
        TaoClientRequest r = new TaoClientRequest();
        r.initFromSerialized(data);
        return r;
    }

    public ProxyRequest parseProxyRequestBytes(byte[] data) {
        TaoProxyRequest r = new TaoProxyRequest();
        r.initFromSerialized(data);
        return r;
    }

    public ProxyResponse parseProxyResponseBytes(byte[] data) {
        TaoProxyResponse r = new TaoProxyResponse();
        r.initFromSerialized(data);
        return r;
    }

    public ServerResponse parseServerResponseBytes(byte[] data) {
        TaoServerResponse r = new TaoServerResponse();
        r.initFromSerialized(data);
        return r;
    }

    public ClientRequest createClientRequest() {
        TaoClientRequest r = new TaoClientRequest();
        return r;
    }

    public ProxyRequest createProxyRequest() {
        TaoProxyRequest r = new TaoProxyRequest();
        return r;
    }

    public ProxyResponse createProxyResponse() {
        TaoProxyResponse r = new TaoProxyResponse();
        return r;
    }

    public ServerResponse createServerResponse() {
        TaoServerResponse r = new TaoServerResponse();
        return r;
    }
}
