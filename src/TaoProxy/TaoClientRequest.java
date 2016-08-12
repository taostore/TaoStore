package TaoProxy;

import Messages.ClientRequest;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Created by ajmagat on 6/3/16.
 */
public class TaoClientRequest implements ClientRequest {
    // The block ID that this request is asking for
    private long mBlockID;

    // The type of request this is
    private int mType;

    // If mType == WRITE, the data that this request wants to put in a block
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
        mData = new byte[Constants.BLOCK_SIZE];
        mRequestID = -1;

        // TODO: default?
        mClientAddress = new InetSocketAddress("localhost", 12345);
    }

    /**
     * @brief Constructor for ClientRequest object
     * @param blockID
     * @param type
     * @param requestID
     * @param address
     */
    public TaoClientRequest(long blockID, int type, long requestID, InetSocketAddress address) {
        mBlockID = blockID;
        mType = type;
        mData = new byte[Constants.BLOCK_SIZE];
        mRequestID = requestID;
        mClientAddress = address;
    }

    /**
     * @brief
     * @param blockID
     * @param type
     * @param requestID
     * @param data
     * @param address
     */
    public TaoClientRequest(long blockID, int type, long requestID, byte[] data, InetSocketAddress address) {
        mBlockID = blockID;
        mType = type;
        mData = new byte[Constants.BLOCK_SIZE];
        System.arraycopy(data, 0, mData, 0, data.length);
        mRequestID = requestID;
        mClientAddress = address;
    }

    /**
     * @brief Constructor that takes in an array of bytes to be parsed as a ClientRequest
     * @param serializedData
     */
    public TaoClientRequest(byte[] serializedData) {
        int startIndex = 0;
        mBlockID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, startIndex, startIndex + 8));
        startIndex += 8;

        mType = Ints.fromByteArray(Arrays.copyOfRange(serializedData, startIndex, startIndex + 4));
        startIndex += 4;

        mRequestID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, startIndex, startIndex + 8));
        startIndex += 8;

        mData = Arrays.copyOfRange(serializedData, startIndex, startIndex + Constants.BLOCK_SIZE);
        startIndex += Constants.BLOCK_SIZE;

        int hostnameSize = Ints.fromByteArray(Arrays.copyOfRange(serializedData, startIndex, startIndex + 4));
        startIndex += 4;

        byte[] hostnameBytes = Arrays.copyOfRange(serializedData, startIndex, startIndex + hostnameSize);
        startIndex += hostnameSize;

        String hostname = new String(hostnameBytes, StandardCharsets.UTF_8);

        int port = Ints.fromByteArray(Arrays.copyOfRange(serializedData, startIndex, startIndex + 4));
        startIndex += 4;

        mClientAddress = new InetSocketAddress(hostname, port);
    }

    public void initFromSerialized(byte[] serialized) {
        int startIndex = 0;
        mBlockID = Longs.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 8));
        startIndex += 8;

        mType = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        startIndex += 4;

        mRequestID = Longs.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 8));
        startIndex += 8;

        mData = Arrays.copyOfRange(serialized, startIndex, startIndex + Constants.BLOCK_SIZE);
        startIndex += Constants.BLOCK_SIZE;

        int hostnameSize = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        startIndex += 4;

        byte[] hostnameBytes = Arrays.copyOfRange(serialized, startIndex, startIndex + hostnameSize);
        startIndex += hostnameSize;

        String hostname = new String(hostnameBytes, StandardCharsets.UTF_8);

        int port = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        startIndex += 4;

        mClientAddress = new InetSocketAddress(hostname, port);
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

    /**
     * @brief Static method to return the serialized size of a ClientRequest
     * @return the size of serialized ClientRequest
     */
    public static int getClientRequestSize() {
        return 8 + 4 + Constants.BLOCK_SIZE + 8;
    }

    /**
     * @brief
     * @return
     */
    public byte[] serializeAsMessage() {
        byte[] serial = serialize();
        byte[] protocolByte = Ints.toByteArray(Constants.CLIENT_REQUEST);
        byte[] sizeByte = Ints.toByteArray(serial.length);
        return Bytes.concat(protocolByte, sizeByte, serial);
    }
}
