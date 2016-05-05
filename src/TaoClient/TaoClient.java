package TaoClient;

import TaoProxy.ClientRequest;
import TaoProxy.Constants;
import TaoProxy.ProxyRequest;
import TaoProxy.ProxyResponse;
import com.google.common.primitives.Ints;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @brief Class to represent the client of TaoStore
 */
public class TaoClient implements Client {
    private InetSocketAddress mProxyAddress;
    private InetSocketAddress mClientAddress;

    /**
     * @brief Default constructor
     */
    public TaoClient() {
        // TODO: what should be default?
        mProxyAddress = new InetSocketAddress("localhost", 12344);
        mClientAddress = new InetSocketAddress("localhost", 12346);
    }

    public TaoClient(String proxyAddress, int proxyPort) {
        mProxyAddress = new InetSocketAddress(proxyAddress, proxyPort);
    }

    @Override
    public byte[] read(long blockID) {
        try {
            // Create object that will be used as a condition
            Object wait = new Object();

            // Create an empty response
            ProxyResponse proxyResponse = new ProxyResponse();

            // Start a server socket to listen for incoming response to this request
            // TODO: Might come out of order
            Runnable r = () -> {
                try {
                    // Create server socket
                    ServerSocket serverSocket = new ServerSocket(mClientAddress.getPort());
                    Socket clientServerSocket = serverSocket.accept();
                    InputStream input = clientServerSocket.getInputStream();

                    byte[] responseBytes = new byte[ProxyResponse.getProxyResponseSize()];

                    input.read(responseBytes, 0, responseBytes.length);

                    byte[] protocolBytes = Arrays.copyOfRange(responseBytes, 0, 4);
                    int messageType = Ints.fromByteArray(protocolBytes);

                    if (messageType == Constants.PROXY_RESPONSE) {
                        proxyResponse.initialize(Arrays.copyOfRange(responseBytes, 4, responseBytes.length));
                    }

                    input.close();
                    clientServerSocket.close();
                    serverSocket.close();
                    synchronized (wait) {
                        wait.notifyAll();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            new Thread(r).start();

            // Get proxy name and port and connect to server
            Socket clientSocket = new Socket(mProxyAddress.getHostName(), mProxyAddress.getPort());

            // Create output stream
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // Create request
            // TODO: generate random request ID
            long requestID = 0;
            ClientRequest request = new ClientRequest(blockID, ClientRequest.READ, requestID, mClientAddress);

            // Send request to proxy
            output.write(request.serializeAsMessage());
            clientSocket.close();

            // Close streams
            output.close();

            // Wait until response
            synchronized (wait) {
                wait.wait();
            }

            return proxyResponse.getReturnData();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public boolean write(long blockID, byte[] data) {
        try {
            Object wait = new Object();

            ProxyResponse proxyResponse = new ProxyResponse();
            Runnable r = () -> {
                try {
                    System.out.println("Client waiting for response");
                    ServerSocket serverSocket = new ServerSocket(mClientAddress.getPort());
                    Socket clientServerSocket = serverSocket.accept();
                    System.out.println("Client got response");
                    InputStream input = clientServerSocket.getInputStream();

                    byte[] protocolBytes = new byte[4];
                    input.read(protocolBytes, 0, protocolBytes.length);
                    int messageType = Ints.fromByteArray(protocolBytes);
                    System.out.println("The messageType is " + messageType);
                    if (messageType == Constants.PROXY_RESPONSE) {
                        byte[] responseBytes = new byte[ProxyResponse.getProxyResponseSize()];
                        input.read(responseBytes);

                        System.out.print("Client says message ");
                        for (byte b : responseBytes) {
                            System.out.print(b);
                        }
                        System.out.println();

                        proxyResponse.initialize(responseBytes);
                        System.out.println("In here doe " + proxyResponse.getClientRequestID());
                    }

                    input.close();
                    clientServerSocket.close();
                    serverSocket.close();
                    synchronized (wait) {
                        wait.notifyAll();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            new Thread(r).start();

            // Get proxy name and port
            Socket clientSocket = new Socket(mProxyAddress.getHostName(), mProxyAddress.getPort());

            // Create output stream
            DataOutputStream output = new DataOutputStream(clientSocket.getOutputStream());

            // Create request
            // TODO: generate random request ID
            long requestID = 3;
            ClientRequest request = new ClientRequest(blockID, ClientRequest.WRITE, requestID, data, mClientAddress);

            // Send request to proxy
            output.write(request.serializeAsMessage());
            clientSocket.close();

            // Close streams
            output.close();

            // Wait until response
            synchronized (wait) {
                wait.wait();
            }

            return proxyResponse.getWriteStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void main(String[] args) {
        TaoClient client = new TaoClient();
    }
}
