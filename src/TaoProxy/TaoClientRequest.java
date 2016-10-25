package TaoProxy;

import Configuration.TaoConfigs;
import Messages.ClientRequest;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * @brief Implementation of a class that implements the ClientRequest message type
 */
public class TaoClientRequest implements ClientRequest {
    // The block ID that this request is asking for
    private long mBlockID;

    // The type of request this is
    private int mType;

    // Either MessageTypes.CLIENT_READ_REQUEST or MessageTypes.CLIENT_WRITE_REQUEST
    private byte[] mData;

    // ID that will uniquely identify this request
    private long mRequestID;

    // The address of the client making the request
    private InetSocketAddress mClientAddress;

    /**
     * @brief Default constructor
     */
    public TaoClientRequest() {
        mBlockID = -1;
        mType = -1;
        mData = new byte[TaoConfigs.BLOCK_SIZE];
        mRequestID = -1;
        mClientAddress = new InetSocketAddress(TaoConfigs.CLIENT_HOSTNAME, TaoConfigs.CLIENT_PORT);
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        int startIndex = 0;
        TaoLogger.logForce("1");
        mBlockID = Longs.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 8));
        startIndex += 8;
        TaoLogger.logForce("2");
        mType = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        startIndex += 4;
        TaoLogger.logForce("3");
        mRequestID = Longs.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 8));
        startIndex += 8;
        TaoLogger.logForce("4");
        mData = Arrays.copyOfRange(serialized, startIndex, startIndex + TaoConfigs.BLOCK_SIZE);
        startIndex += TaoConfigs.BLOCK_SIZE;
        TaoLogger.logForce("5");
        int hostnameSize = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        startIndex += 4;
        TaoLogger.logForce("6");
        byte[] hostnameBytes = Arrays.copyOfRange(serialized, startIndex, startIndex + hostnameSize);
        startIndex += hostnameSize;
        TaoLogger.logForce("7");
        String hostname = new String(hostnameBytes, StandardCharsets.UTF_8);
        TaoLogger.logForce("8");
        int port = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        startIndex += 4;
        TaoLogger.logForce("9");
        mClientAddress = new InetSocketAddress(hostname, port);
        TaoLogger.logForce("10");
    }

    @Override
    public long getBlockID() {
        return mBlockID;
    }

    @Override
    public void setBlockID(long blockID) {
        mBlockID = blockID;
    }

    @Override
    public int getType() {
        return mType;
    }

    @Override
    public void setType(int type) {
        mType = type;
    }

    @Override
    public byte[] getData() {
        return mData;
    }

    @Override
    public void setData(byte[] data) {
        System.arraycopy(data, 0, mData, 0, mData.length);
    }

    @Override
    public long getRequestID() {
        return mRequestID;
    }

    @Override
    public void setRequestID(long requestID) {
        mRequestID = requestID;
    }

    @Override
    public InetSocketAddress getClientAddress() {
        return mClientAddress;
    }

    @Override
    public void setClientAddress(InetSocketAddress clientAddress) {
        mClientAddress = clientAddress;
    }

    @Override
    public byte[] serialize() {
        byte[] blockIDBytes = Longs.toByteArray(mBlockID);
        byte[] typeBytes = Ints.toByteArray(mType);
        byte[] idBytes = Longs.toByteArray(mRequestID);
        byte[] hostnameLengthBytes = Ints.toByteArray(mClientAddress.getHostName().length());
        byte[] hostnameBytes = mClientAddress.getHostName().getBytes(StandardCharsets.UTF_8);
        byte[] portBytes = Ints.toByteArray(mClientAddress.getPort());

        return Bytes.concat(blockIDBytes, typeBytes, idBytes, mData, hostnameLengthBytes, hostnameBytes, portBytes);
    }

    @Override
    public boolean equals(Object obj) {
        if ( ! (obj instanceof ClientRequest) ) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        // Two requests are equal if they have the same request ID
        TaoClientRequest rhs = (TaoClientRequest) obj;

        if (mRequestID != rhs.getRequestID()) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRequestID);
    }

}
