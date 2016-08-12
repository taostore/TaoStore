package TaoProxy;

import Messages.ProxyResponse;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * Created by ajmagat on 6/3/16.
 */
public class TaoProxyResponse implements ProxyResponse {
    private long mClientRequestID;
    private byte[] mReturnData;
    private boolean mWriteStatus;

    /**
     * @brief Default constructor
     */
    public TaoProxyResponse() {
        mClientRequestID = -1;
        mReturnData = new byte[Constants.BLOCK_SIZE];
        mWriteStatus = false;
    }

    /**
     * @brief
     * @param clientRequestID
     */
    public TaoProxyResponse(long clientRequestID) {
        mClientRequestID = clientRequestID;
    }

    /**
     * @brief
     * @param clientRequestID
     * @param returnData
     */
    public TaoProxyResponse(long clientRequestID, byte[] returnData) {
        mClientRequestID = clientRequestID;
        mReturnData = returnData;
        mWriteStatus = false;
    }

    /**
     * @brief
     * @param clientRequestID
     * @param writeStatus
     */
    public TaoProxyResponse(long clientRequestID, boolean writeStatus) {
        mClientRequestID = clientRequestID;
        mReturnData = new byte[Constants.BLOCK_SIZE];
        mWriteStatus = writeStatus;
    }

    /**
     * @brief
     * @param serializedData
     */
    public TaoProxyResponse(byte[] serializedData) {
        initFromSerialized(serializedData);
    }


    public void initFromSerialized(byte[] serialized) {
        int startIndex = 0;
        mClientRequestID = Longs.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 8));
        startIndex += 8;

        mReturnData = Arrays.copyOfRange(serialized, startIndex, startIndex + Constants.BLOCK_SIZE);
        startIndex += Constants.BLOCK_SIZE;

        int writeStatus = Ints.fromByteArray(Arrays.copyOfRange(serialized, startIndex, startIndex + 4));
        mWriteStatus = writeStatus == 1 ? true : false;
    }

    /**
     * @brief
     * @return
     */
    public long getClientRequestID() {
        return mClientRequestID;
    }

    public void setClientRequestID(long requestID) {
        mClientRequestID = requestID;
    }

    /**
     * @brief
     * @return
     */
    public byte[] getReturnData() {
        return mReturnData;
    }

    public void setReturnData(byte[] data) {
        mReturnData = data;
    }

    /**
     * @brief
     * @return
     */
    public boolean getWriteStatus() {
        return mWriteStatus;
    }

    public void setWriteStatus(boolean status) {
        mWriteStatus = status;
    }

    /**
     * @brief
     * @return
     */
    public static int getProxyResponseSize() {
        return 8 + Constants.BLOCK_SIZE + 4;
    }

    /**
     * @brief
     * @return
     */
    @Override
    public byte[] serialize() {
        byte[] clientIDBytes = Longs.toByteArray(mClientRequestID);
        int writeStatusInt = mWriteStatus ? 1 : 0;
        byte[] writeStatusBytes = Ints.toByteArray(writeStatusInt);
        return Bytes.concat(clientIDBytes, mReturnData, writeStatusBytes);
    }

    /**
     * @brief
     * @return
     */
    public byte[] serializeAsMessage() {
        byte[] serial = serialize();
        byte[] protocolByte = Ints.toByteArray(Constants.PROXY_RESPONSE);
        return Bytes.concat(protocolByte, serial);
    }
}
