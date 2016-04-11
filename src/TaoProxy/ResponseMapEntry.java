package TaoProxy;

/**
 * @brief Method to represent an entry into the processor's response map
 */
public class ResponseMapEntry {
    // The status of whether or not the server has replied to the request corresponding to this entry
    private boolean mReturned;

    // The data returned from the server corresponding to this request
    private byte[] mData;

    /**
     * @brief Default constructor
     */
    public ResponseMapEntry() {
        mReturned = false;
        mData = null;
    }

    /**
     * @brief Accessor for mReturned
     * @return whether or not the request corresponding to this entry has been returned or not
     */
    public boolean getRetured() {
        return mReturned;
    }

    /**
     * @brief Accessor for mData
     * @return the data returned from the server corresponding to this request. null if no reply from server
     */
    public byte[] getData() {
        return mData;
    }

    /**
     * @brief Mutator for mReturned
     * @param returned
     */
    public void setReturned(boolean returned) {
        mReturned = returned;
    }


    /**
     * @brief Mutator for mData
     * @param data
     */
    public void setData(byte[] data) {
        mData = data.clone();
    }
}
