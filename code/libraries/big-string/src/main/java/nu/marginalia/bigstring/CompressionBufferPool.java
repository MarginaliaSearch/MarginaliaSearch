package nu.marginalia.bigstring;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/** To avoid contention on the compression buffer, while keeping allocation churn low,
 * we use a pool of buffers, randomly selected allocated upon invocation
 * <p>
 *  @see CompressionBuffer CompressionBuffer
 * */
public class CompressionBufferPool {
    private static final int BUFFER_COUNT = 16;
    private final CompressionBuffer[] destBuffer;

    public CompressionBufferPool() {
        destBuffer = new CompressionBuffer[BUFFER_COUNT];
        Arrays.setAll(destBuffer, i -> new CompressionBuffer());
    }

    /** Get the buffer for the current thread */
    public CompressionBuffer bufferForThread() {
        int idx = ThreadLocalRandom.current().nextInt(0, BUFFER_COUNT);

        return destBuffer[idx];
    }
}
