package nu.marginalia.linkgraph.io;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class DomainLinksWriter implements AutoCloseable {
    private final DataOutputStream stream;

    public DomainLinksWriter(Path fileName) throws IOException {
        this.stream = new DataOutputStream(Files.newOutputStream(fileName,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)
        );
    }

    public void write(int sourceDomainId, int destDomainId) throws IOException {
        stream.writeLong(Integer.toUnsignedLong(sourceDomainId) << 32
                          | Integer.toUnsignedLong(destDomainId));
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
