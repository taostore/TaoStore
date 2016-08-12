package TaoProxy;

import Messages.ProxyRequest;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

import java.util.Arrays;

/**
 *
 */
public class TaoProxyRequest implements ProxyRequest {
    private int mType;

    // If mType == 0, this is the path that we are interested in reading
    private long mReadPathID;

    // Amount of bytes in a path
    private int mPathSize;
    private byte[] mDataToWrite;

    /**
     * @brief Default constructor
     */
    public TaoProxyRequest() {
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
    public TaoProxyRequest(int type, long pathID) {
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
    public TaoProxyRequest(int type, int pathSize, byte[] dataToWrite) {
        mType = type;
        mReadPathID = -1;
        mPathSize = pathSize;
        mDataToWrite = dataToWrite;
    }

    /**
     * @brief Constructor that takes in an array of bytes to be parsed as a ProxyRequest
     * @param serializedData
     */
    public TaoProxyRequest(byte[] serializedData) {
        mType = Ints.fromByteArray(Arrays.copyOfRange(serializedData, 0, 4));

        if (mType == Constants.PROXY_READ_REQUEST) {
            mReadPathID = Longs.fromByteArray(Arrays.copyOfRange(serializedData, 4, 12));
            mPathSize = -1;
            mDataToWrite = null;
        } else if (mType == Constants.PROXY_WRITE_REQUEST) {
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

    public void setType(int type) {
        mType = type;
    }

    public void initFromSerialized(byte[] serialized) {
        mType = Ints.fromByteArray(Arrays.copyOfRange(serialized, 0, 4));

        if (mType == Constants.PROXY_READ_REQUEST) {
            mReadPathID = Longs.fromByteArray(Arrays.copyOfRange(serialized, 4, 12));
            mPathSize = -1;
            mDataToWrite = null;
        } else if (mType == Constants.PROXY_WRITE_REQUEST) {
            // TODO: Change this to not need paths anymore
            mReadPathID = -1;
            mPathSize = Ints.fromByteArray(Arrays.copyOfRange(serialized, 4, 8));

            int serializedIndex = 8;
            int dataToWriteIndex = 0;
            mDataToWrite = new byte[serialized.length - 8];

            while (serializedIndex < serialized.length) {
                System.arraycopy(serialized, serializedIndex, mDataToWrite, dataToWriteIndex, mPathSize);
                serializedIndex += mPathSize;
                dataToWriteIndex += mPathSize;
            }
        }
    }

    public int getPathSize() {
        return mPathSize;
    }

    public void setPathSize(int pathSize) {
        mPathSize = pathSize;
    }

    @Override
    public long getPathID() {
        return mReadPathID;
    }

    public void setPathID(long pathID) {
        mReadPathID = pathID;
    }

    public byte[] getDataToWrite() {
        return mDataToWrite;
    }

    public void setDataToWrite(byte[] data) {
        mDataToWrite = data;
    }

    public static int getProxyWriteRequestSize() {
        return 4 + Constants.WRITE_BACK_THRESHOLD * TaoPath.getPathSize();
    }

    /**
     * @brief
     * @return
     */
    @Override
    public byte[] serialize() {
        byte[] returnData = null;

        if (mType == Constants.PROXY_READ_REQUEST) {
            byte[] typeBytes = Ints.toByteArray(mType);
            byte[] pathBytes = Longs.toByteArray(mReadPathID);

            returnData = Bytes.concat(typeBytes, pathBytes);
        } else if (mType == Constants.PROXY_WRITE_REQUEST) {
            byte[] typeBytes = Ints.toByteArray(mType);
            byte[] pathSizeBytes = Ints.toByteArray(mPathSize);

            returnData = Bytes.concat(typeBytes, pathSizeBytes, mDataToWrite);
        }

        return returnData;
    }
}
