package nu.marginalia.util.array.page;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public interface PartitionPage {

    default void write(FileChannel channel) throws IOException {
        var byteBuffer = getByteBuffer();

        byteBuffer.clear();

        while (byteBuffer.position() < byteBuffer.limit()) {
            channel.write(byteBuffer);
        }

        byteBuffer.clear();
    }

    ByteBuffer getByteBuffer();
}
