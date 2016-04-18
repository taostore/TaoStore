package TaoProxyTest;

import TaoProxy.TaoProcessor;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @brief
 */
public class TaoProcessorTest {
    @Test
    public void flush() throws Exception {
        TaoProcessor processor = new TaoProcessor();

        processor.flush(0);
    }

}