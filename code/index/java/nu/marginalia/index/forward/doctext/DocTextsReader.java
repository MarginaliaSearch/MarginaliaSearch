package nu.marginalia.index.forward.doctext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DocTextsReader implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DocTextsReader.class);

    private final FileChannel channel;

    public DocTextsReader(Path file) throws IOException {
        channel = FileChannel.open(file, StandardOpenOption.READ);
    }

    @Nullable
    public String read(DocTextDecoder decoder, long encodedLocation) {
        if (DocTextsCodec.isAbsent(encodedLocation)) {
            return null;
        }

        try {
            return decoder.read(channel,
                    DocTextsCodec.decodeStartOffset(encodedLocation),
                    DocTextsCodec.decodeSize(encodedLocation));
        }
        catch (IOException ex) {
            logger.error("Failed to read document text", ex);
            return null;
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
