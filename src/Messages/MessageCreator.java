package Messages;

/**
 *
 */
public interface MessageCreator {
    ClientRequest parseClientRequestBytes(byte[] data);

    ProxyRequest parseProxyRequestBytes(byte[] data);

    ProxyResponse parseProxyResponseBytes(byte[] data);

    ServerResponse parseServerResponseBytes(byte[] data);

    ClientRequest createClientRequest();

    ProxyRequest createProxyRequest();

    ProxyResponse createProxyResponse();

    ServerResponse createServerResponse();
}
