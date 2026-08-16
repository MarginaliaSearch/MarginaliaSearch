package nu.marginalia.index.forward.doctext;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdDecompressCtx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;


/** Stateful decoder for zstd-compressed document texsts.
 * Not thread safe.
 * */
public class DocTextDecoder implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DocTextDecoder.class);

    private static final long MAX_TEXT_LENGTH = 1L << 27;

    private final ZstdDecompressCtx ctx = new ZstdDecompressCtx();

    private byte[] compressed = new byte[16 * 1024];
    private byte[] decompressed = new byte[64 * 1024];

    @Nullable
    public String read(FileChannel channel, long offset, int size) throws IOException {
        if (compressed.length < size) {
            compressed = new byte[Math.max(size, 2 * compressed.length)];
        }

        ByteBuffer buffer = ByteBuffer.wrap(compressed, 0, size);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, offset + buffer.position()) < 0)
                return null;
        }

        try {
            long textLength = Zstd.getFrameContentSize(compressed, 0, size);
            if (textLength <= 0 || textLength > MAX_TEXT_LENGTH) {
                logger.warn("Implausible document text size {}", textLength);
                return null;
            }

            if (decompressed.length < textLength) {
                decompressed = new byte[Math.max((int) textLength, 2 * decompressed.length)];
            }

            int length = ctx.decompressByteArray(decompressed, 0, decompressed.length, compressed, 0, size);

            return new String(decompressed, 0, length, StandardCharsets.UTF_8);
        }
        catch (RuntimeException ex) {
            logger.warn("Failed to decompress document text", ex);
            return null;
        }
    }

    @Override
    public void close() {
        ctx.close();
    }
}
