package nu.marginalia.sideload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

public class SideloadHelper {
    public static String getCrc32FileHash(Path file) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        try (var channel = Files.newByteChannel(file)) {
            CRC32 crc = new CRC32();

            while (channel.read(buffer) > 0) {
                buffer.flip();
                crc.update(buffer);
                buffer.clear();
            }

            return Long.toHexString(crc.getValue());
        }
    }
}
