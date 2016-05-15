package TaoProxy;

import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @brief Class that represents a request from a proxy to the server
 */
public class ProxyRequest {
    public static final int READ = 0;
    public static final int WRITE = 1;

    // 0 = read path
    // 1 = write paths
    private int mType;

    // If mType == 0, this is the path that we are interested in reading
    private long mReadPathID;

    private int mPathSize;
    private byte[] mDataToWrite;

    /**
     * @brief Default constructor
     */
    public ProxyRequest() {
        mType = -1;
        mReadPathID = -1;
        mPathSize = -1;
        mDataToWrite = null;
    }

    /**
     * @brief Constructor for a ProxyRequest of type READ
     * @param type
     * @param pathID
     */
    public ProxyRequest(int type, long pathID) {
        mType = type;
        mReadPathID = pathID;
        mPathSize = -1;
        mDataToWrite = null;
    }

    /**
     * @brief Constructor for a ProxyRequest of type WRITE
     * @param type
     * @param pathSize
     * @param dataToWrite
     */
    public ProxyRequest(int type, int pathSize, byte[] dataToWrite) {
        mType = type;
        mReadPathID = -1;
        mPathSize = pathSize;
        mDataToWrite = dataToWrite;
    }

    /**
     * @brief Constructor that takes in an array of bytes to be parsed as a ProxyRequest
     * @param serializedData
     */
    public ProxyRequest(byte[] serializedData) {
        mType = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 0, 4));

        if (mType == ProxyRequest.READ) {
            mReadPathID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, 4, 12));
            mPathSize = -1;
            mDataToWrite = null;
        } else if (mType == ProxyRequest.WRITE) {
            // TODO: Change this to not need paths anymore
            mReadPathID = -1;
            mPathSize = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 4, 8));

            int serializedIndex = 8;
            int dataToWriteIndex = 0;
            mDataToWrite = new byte[serializedData.length - 8];

            while (serializedIndex < serializedData.length) {
                System.arraycopy(serializedData, serializedIndex, mDataToWrite, dataToWriteIndex, mPathSize);
                serializedIndex += mPathSize;
                dataToWriteIndex += mPathSize;
            }
        }
    }

    public int getType() {
        return mType;
    }

    public int getPathSize() {
        return mPathSize;
    }

    public long getPathID() {
        return mReadPathID;
    }

    public byte[] getDataToWrite() {
        return mDataToWrite;
    }

    public static int getProxyReadRequestSize() {
        return 4 + 8;
    }

    public static int getProxyWriteRequestSize() {
        return 4 + Constants.WRITE_BACK_THRESHOLD * Path.getPathSize();
    }

    /**
     * @brief
     * @return
     */
    public byte[] serialize() {
        byte[] returnData = null;

        if (mType == ProxyRequest.READ) {
            byte[] typeBytes = Ints.toByteArray(mType);
            byte[] pathBytes = Longs.toByteArray(mReadPathID);

            returnData = Bytes.concat(typeBytes, pathBytes);
        } else if (mType == ProxyRequest.WRITE) {
            byte[] typeBytes = Ints.toByteArray(mType);
            byte[] pathSizeBytes = Ints.toByteArray(mPathSize);

            returnData = Bytes.concat(typeBytes, pathSizeBytes, mDataToWrite);
        }

        return returnData;
    }

    /**
     * @brief
     * @return
     */
    public byte[] serializeAsMessage() {
        byte[] serial = serialize();

        byte[] protocolByte = null;
        if (mType == ProxyRequest.READ) {
            protocolByte = Ints.toByteArray(Constants.PROXY_READ_REQUEST);
        } else if (mType == ProxyRequest.WRITE) {
            protocolByte = Ints.toByteArray(Constants.PROXY_WRITE_REQUEST);
        }

        return Bytes.concat(protocolByte, serial);
    }
}
