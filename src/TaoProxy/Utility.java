package TaoProxy;

import Configuration.TaoConfigs;
import com.google.common.primitives.Bytes;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Map;

/**
 * @brief
 */
public class Utility {
    public static SecretKey mSecretKey;

    public static boolean[] getPathFromPID(long pathID, int height) {
        boolean[] ret = new boolean[height];

        // The bit of the path ID we are currently checking
        // If 0, then descend left down path, else right
        int direction;

        // The level we are currently checking to see if it is a right or left
        int level = ret.length - 1;

        while (pathID > 0) {
            direction = (int) pathID % 2;

            if (direction == 0) {
                ret[level] = false;
            } else {
                ret[level] = true;
            }
            pathID = pathID >> 1;
            level--;
        }

        return ret;
    }

    public static long getGreatestCommonLevel(long pathOne, long pathTwo) {
        // TODO: Check size
        long indexBit = 1 << (TaoConfigs.TREE_HEIGHT - 1);
        int greatestLevel = 0;

        while (greatestLevel < TaoConfigs.TREE_HEIGHT) {
            if ( (pathOne & indexBit) == (pathTwo & indexBit) ) {
                indexBit = indexBit >> 1;
                greatestLevel++;
            } else {
                break;
            }
        }

        return greatestLevel;
    }

    public static byte[] encrypt(byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            SecretKeySpec k = new SecretKeySpec(mSecretKey.getEncoded(), "AES");
            c.init(Cipher.ENCRYPT_MODE, k);
            byte[] encryptedData = c.doFinal(data);

            return Bytes.concat(c.getIV(), encryptedData);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public static byte[] decrypt(byte[] encryptedData) {
        try {
            byte[] iv = Arrays.copyOfRange(encryptedData, 0, 16);
            SecretKeySpec k = new SecretKeySpec(mSecretKey.getEncoded(), "AES");
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            c.init(Cipher.DECRYPT_MODE, k, new IvParameterSpec(iv));
            return c.doFinal(Arrays.copyOfRange(encryptedData, 16, encryptedData.length));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
