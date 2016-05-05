package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 * @brief
 */
public class ProxyResponse {
    private long mClientRequestID;
    private byte[] mReturnData;
    private boolean mWriteStatus;

    /**
     * @brief Default constructor
     */
    public ProxyResponse() {

    }

    /**
     * @brief
     * @param clientRequestID
     */
    public ProxyResponse(long clientRequestID) {
        mClientRequestID = clientRequestID;
    }

    /**
     * @brief
     * @param clientRequestID
     * @param returnData
     */
    public ProxyResponse(long clientRequestID, byte[] returnData) {
        mClientRequestID = clientRequestID;
        mReturnData = returnData;
        mWriteStatus = false;
    }

    /**
     * @brief
     * @param clientRequestID
     * @param writeStatus
     */
    public ProxyResponse(long clientRequestID, boolean writeStatus) {
        mClientRequestID = clientRequestID;
        mReturnData = new byte[Constants.BLOCK_SIZE];
        mWriteStatus = writeStatus;
    }

    /**
     * @brief
     * @param serializedData
     */
    public ProxyResponse(byte[] serializedData) {
        initialize(serializedData);
    }

    /**
     * @brief
     * @param serializedData
     */
    public void initialize(byte[] serializedData) {
        int startIndex = 0;
        mClientRequestID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, startIndex, startIndex + 8));
        startIndex += 8;

        mReturnData = Arrays.copyOfRange(serializedData, startIndex, startIndex + Constants.BLOCK_SIZE);
        startIndex += Constants.BLOCK_SIZE;

        int writeStatus = Ints.fromByteArray(Arrays.copyOfRange(serializedData, startIndex, startIndex + 4));
        mWriteStatus = writeStatus == 1 ? true : false;
        System.out.println("Write status is " + mWriteStatus);
    }

    /**
     * @brief
     * @return
     */
    public long getClientRequestID() {
        return mClientRequestID;
    }

    /**
     * @brief
     * @return
     */
    public byte[] getReturnData() {
        return mReturnData;
    }

    /**
     * @brief
     * @return
     */
    public boolean getWriteStatus() {
        return mWriteStatus;
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
