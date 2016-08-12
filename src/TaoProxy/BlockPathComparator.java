package TaoProxy;

import Configuration.TaoConfigs;

import java.util.Comparator;

/**
 * @brief Class used to compare blocks against a given path ID to determine which block is better suited to be flushed
 */
public class BlockPathComparator implements Comparator<Block> {
    // The path ID that all blocks will be compared to
    private long mPathID;

    // The directions for the target path
    private boolean[] mPath;

    // The position map
    private TaoPositionMap mMap;

    /**
     * @brief Constructor that takes in a pathID that this comparator will use to compare blocks
     * @param pathID
     */
    public BlockPathComparator(long pathID, TaoPositionMap map) {
        mPathID = pathID;
        mPath = Utility.getPathFromPID(mPathID, TaoConfigs.TREE_HEIGHT);
        mMap = map;
    }

    @Override
    public int compare(Block b1, Block b2) {
        // Get the path IDs of both blocks
        long blockOnePID = mMap.getBlockPosition(b1.getBlockID());
        long blockTwoPID = mMap.getBlockPosition(b2.getBlockID());

        // Get the greatest level shared with the target path ID
        long blockOneScore = Utility.getGreatestCommonLevel(mPathID, blockOnePID);
        long blockTwoScore = Utility.getGreatestCommonLevel(mPathID, blockTwoPID);

        // Return the result
        if (blockOneScore > blockTwoScore) {
            return -1;
        } else if (blockOneScore < blockTwoScore) {
            return 1;
        }

        return 0;
    }
}
