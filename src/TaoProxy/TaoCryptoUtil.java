package TaoProxy;

import Configuration.TaoConfigs;
import com.google.common.primitives.Bytes;
import com.google.common.primitives.Longs;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.Random;

/**
 * Created by ajmagat on 6/2/16.
 */
public class TaoCryptoUtil implements CryptoUtil {
    private SecretKey mSecretKey;

    public TaoCryptoUtil() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            mSecretKey = keyGen.generateKey();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TaoCryptoUtil(SecretKey key) {
        mSecretKey = key;
    }

    @Override
    public byte[] encrypt(byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
            SecretKeySpec k = new SecretKeySpec(mSecretKey.getEncoded(), "AES");
            c.init(Cipher.ENCRYPT_MODE, k);
            byte[] encryptedData = c.doFinal(data);
            TaoLogger.log("encrypted data before iv concat size " + encryptedData.length);
            TaoLogger.log("iv size is " + c.getIV().length);
            return Bytes.concat(c.getIV(), encryptedData);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public byte[] decrypt(byte[] encryptedData) {
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

    @Override
    public byte[] encryptPath(Path p) {
        try {
            byte[] idBytes = Longs.toByteArray(p.getID());
            byte[] bucketBytes = p.serializeBuckets();

            int bucketSize = TaoBucket.getBucketSize();
            TaoLogger.log("the bucket size in taocryptoutil is " + bucketSize);
            TaoLogger.log("calculated bucket size is " + TaoConfigs.ENCRYPTED_BUCKET_SIZE);
            // Encrypt the first bucket
            // TODO: pad buckets for encryption
            // byte[] encryptedBuckets = encrypt(Arrays.copyOfRange(bucketBytes, 0, bucketSize));
//            byte[] unencryptedBucket = padBytes(Arrays.copyOfRange(bucketBytes, 0, bucketSize));
            byte[] unencryptedBucket = Arrays.copyOfRange(bucketBytes, 0, bucketSize);

            byte[] encryptedBuckets = encrypt(unencryptedBucket);
            TaoLogger.log("first encrypted bucket has size " + encryptedBuckets.length);

            TaoLogger.log("height of path is " + p.getPathHeight());
            for (int i = 1; i < p.getPathHeight() +1; i++) {
                unencryptedBucket = Arrays.copyOfRange(bucketBytes, bucketSize * i , bucketSize + bucketSize * i);
                TaoLogger.log("size of the unencrypted padded bucket is " + unencryptedBucket.length);
                byte[] temp = encrypt(unencryptedBucket);
                TaoLogger.log("====== size of encrypted bucket is " + temp.length);
                encryptedBuckets = Bytes.concat(encryptedBuckets, temp);
            }

            return Bytes.concat(idBytes, encryptedBuckets);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private byte[] padBytes(byte[] toPad) {
        // TODO: when will i need this?
        int neededPadding = ((int) TaoConfigs.ENCRYPTED_BUCKET_SIZE) - TaoConfigs.IV_SIZE - toPad.length;

        if (neededPadding > 0) {
            byte[] zeroBytes = new byte[neededPadding];
            return Bytes.concat(toPad, zeroBytes);
        } else {
            return toPad;
        }
    }

    @Override
    public Path decryptPath(byte[] data) {
        try {
            long id = Longs.fromByteArray(Arrays.copyOfRange(data, 0, 8));
            Path p = new TaoPath(id);
          //  int metadata = 8;
            int bucketSize = TaoBucket.getBucketSize();
            int offset1 = 8;
            for (int i = 0; i < TaoConfigs.TREE_HEIGHT + 1; i++) {
                TaoLogger.log("decrypting bucket " + i + " of " + (TaoConfigs.TREE_HEIGHT + 1));
                int offset = offset1 + i * (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE;
                byte[] kek = Arrays.copyOfRange(data, offset, (int) TaoConfigs.ENCRYPTED_BUCKET_SIZE + offset);
                TaoLogger.log("encrypted bucket about to be decrypted has size " + kek.length);
                byte[] decryptedBucket = decrypt(kek);
                decryptedBucket = Arrays.copyOf(decryptedBucket, bucketSize);
                TaoLogger.log("decryptedBucket has size " + decryptedBucket.length);
                Bucket b = new TaoBucket();
                b.initFromSerialized(decryptedBucket);
                p.addBucket(b);

                TaoLogger.log("Serialized path looks like ");
               // int p = 0;
                for (Block by : b.getBlocks()) {
                   // for (byte byt : by.getData()) {
                    TaoLogger.log("the block id here is " + by.getBlockID());
                 //   }

//                    p++;
                }
                TaoLogger.log();
             //   System.exit(1);
            }

            return p;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public int getRandomPathID() {
        Random r = new Random();
        return r.nextInt(1 << TaoConfigs.TREE_HEIGHT);
        // return 0;
    }
}
