package TaoProxy;

import Configuration.TaoConfigs;
import Messages.ProxyResponse;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * @brief Implementation of a class that implements the ProxyResponse message type
 */
public class TaoProxyResponse implements ProxyResponse {
    // The original client request ID
    private long mClientRequestID;

    // The data from the read block if responding to a read request
    private byte[] mReturnData;

    // The status of the request write if responding to a write request
    private boolean mWriteStatus;

    /**
     * @brief Default constructor
     */
    public TaoProxyResponse() {
        mClientRequestID = -1;
        mReturnData = new byte[TaoConfigs.BLOCK_SIZE];
        mWriteStatus = false;
    }

    /**
     * @brief
     * @param clientRequestID
     * @param writeStatus
     */
    public TaoProxyResponse(long clientRequestID, boolean writeStatus) {
        mClientRequestID = clientRequestID;
        mReturnData = new byte[TaoConfigs.BLOCK_SIZE];
        mWriteStatus = writeStatus;
    }

    /**
     * @brief
     * @param serializedData
     */
    public TaoProxyResponse(byte[] serializedData) {
        initFromSerialized(serializedData);
    }

    @Override
    public void initFromSerialized(byte[] serialized) {
        int startIndex = 0;
        mClientRequestID = Longs.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 8));
        startIndex += 8;

        mReturnData = Arrays.copyOfRange(serialized, startIndex, startIndex + TaoConfigs.BLOCK_SIZE);
        startIndex += TaoConfigs.BLOCK_SIZE;

        int writeStatus = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        mWriteStatus = writeStatus == 1 ? true : false;
    }

    @Override
    public long getClientRequestID() {
        return mClientRequestID;
    }

    @Override
    public void setClientRequestID(long requestID) {
        mClientRequestID = requestID;
    }

    @Override
    public byte[] getReturnData() {
        return mReturnData;
    }

    @Override
    public void setReturnData(byte[] data) {
        mReturnData = data;
    }

    @Override
    public boolean getWriteStatus() {
        return mWriteStatus;
    }

    @Override
    public void setWriteStatus(boolean status) {
        mWriteStatus = status;
    }

    @Override
    public byte[] serialize() {
        byte[] clientIDBytes = Longs.toByteArray(mClientRequestID);
        int writeStatusInt = mWriteStatus ? 1 : 0;
        byte[] writeStatusBytes = Ints.toByteArray(writeStatusInt);
        return Bytes.concat(clientIDBytes, mReturnData, writeStatusBytes);
    }
}
