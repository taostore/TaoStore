package TaoProxyTest;

import Configuration.TaoConfigs;
import Messages.ClientRequest;
import Messages.MessageCreator;
import Messages.MessageTypes;
import Messages.ProxyRequest;
import TaoProxy.*;
import org.junit.Test;

import javax.crypto.KeyGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 *
 */
public class TaoMessageCreatorTest {
    @Test
    public void testClientRequest() {

    }

    @Test
    public void testProxyRequest() {

    }

    @Test
    public void testProxyWriteRequest() {
        TaoConfigs.initConfiguration();

        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance("AES");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        keyGen.init(128);
        CryptoUtil cryptoUtil = new TaoCryptoUtil(keyGen.generateKey());

        PathCreator pathCreator = new TaoBlockCreator();
        MessageCreator messageCreator = new TaoMessageCreator();

        // Create empty path
        long pathID = 9;
        TaoPath testPath = new TaoPath(pathID);

        // Create empty buckets
        TaoBucket[] testBuckets = new TaoBucket[TaoConfigs.TREE_HEIGHT + 1];

        // Fill in each bucket
        for (int i = 0; i < testBuckets.length; i++) {
            // Create blocks for bucket
            TaoBlock[] testBlocks = new TaoBlock[TaoConfigs.BLOCKS_IN_BUCKET];
            byte[] bytes = new byte[TaoConfigs.BLOCK_SIZE];

            testBuckets[i] = new TaoBucket();

            for (int j = 0; j < testBlocks.length; j++) {
                int blockID = Integer.parseInt(Integer.toString(i) + Integer.toString(j));
                testBlocks[j] = new TaoBlock(blockID);
                Arrays.fill(bytes, (byte) blockID);
                testBlocks[j].setData(bytes);

                testBuckets[i].addBlock(testBlocks[j], 1);
            }

            testPath.addBucket(testBuckets[i]);
        }

        // Create empty paths and serialize
        Path defaultPath = pathCreator.createPath();
        defaultPath.setPathID(9);
        byte[] dataToWrite = cryptoUtil.encryptPath(testPath);
        int pathSize = dataToWrite.length;

        // Create a proxy write request
        ProxyRequest writebackRequest = messageCreator.createProxyRequest();
        writebackRequest.setType(MessageTypes.PROXY_WRITE_REQUEST);
        writebackRequest.setPathSize(pathSize);
        writebackRequest.setDataToWrite(dataToWrite);

        byte[] serialized = writebackRequest.serialize();

        ProxyRequest fromSerialized = messageCreator.parseProxyRequestBytes(serialized);
        assertEquals(writebackRequest.getType(), fromSerialized.getType());
        assertEquals(writebackRequest.getPathSize(), fromSerialized.getPathSize());
        assertTrue(Arrays.equals(writebackRequest.getDataToWrite(), fromSerialized.getDataToWrite()));
    }

    @Test
    public void testProxyResponse() {
        MessageCreator messageCreator = new TaoMessageCreator();

    }

    @Test
    public void testServerResponse() {
        MessageCreator messageCreator = new TaoMessageCreator();

    }
}