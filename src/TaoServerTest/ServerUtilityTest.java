package TaoServerTest;

import TaoServer.ServerUtility;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by ajmagat on 5/15/16.
 */
public class ServerUtilityTest {
    @Test
    public void testGetPathFromPID() {
        boolean[] array = ServerUtility.getPathFromPID(15, 4);
        for (Boolean b : array) {
            System.out.println("Turn is " + b);
        }
    }
}