package TaoServerTest;

import Configuration.TaoConfigs;
import TaoServer.ServerUtility;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by ajmagat on 5/15/16.
 */
public class ServerUtilityTest {
    @Test
    public void testGetPathFromPID() {
//        boolean[] array = ServerUtility.getPathFromPID(15, 4);
//        for (Boolean b : array) {
//            System.out.println("Turn is " + b);
//        }
        // 246420
        // 3942720 = 1000 items
        // 999997440 = 1 gb
        // 268435456 = 256 mb
        // 536870912 = 512 mb
        // 9856800
        TaoConfigs.initConfiguration(536870912);

        int numServers = 1;
        int mServerTreeHeight = TaoConfigs.TREE_HEIGHT;

        if (numServers > 1) {
            int levelSavedOnProxy = (numServers / 2);
            mServerTreeHeight -= levelSavedOnProxy;
        }
        System.out.println("Encrypted bucket size is " + TaoConfigs.ENCRYPTED_BUCKET_SIZE);
        System.out.println("Total tree height is " + TaoConfigs.TREE_HEIGHT);


        System.out.println("Total size of this tree is " + ServerUtility.calculateSize(mServerTreeHeight, TaoConfigs.ENCRYPTED_BUCKET_SIZE));

        System.out.println("Total data items can be " + ((ServerUtility.calculateSize(mServerTreeHeight, TaoConfigs.ENCRYPTED_BUCKET_SIZE) / TaoConfigs.ENCRYPTED_BUCKET_SIZE) * 4 ));


    }
}