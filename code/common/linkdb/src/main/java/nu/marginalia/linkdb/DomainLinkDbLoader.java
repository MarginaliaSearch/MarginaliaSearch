package nu.marginalia.linkdb;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DomainLinkDbLoader implements AutoCloseable {
    private final DataInputStream stream;
    private final Path filename;

    private long nextVal;

    public DomainLinkDbLoader(Path filename) throws IOException {
        this.stream = new DataInputStream(Files.newInputStream(filename));
        this.filename = filename;
    }

    public int size() throws IOException {
        return (int) (Files.size(filename) / 8);
    }

    public boolean next() {
        try {
            nextVal = stream.readLong();
            return true;
        }
        catch (IOException ex) {
            return false;
        }
    }

    public int getSource() {
        return (int) (nextVal >>> 32);
    }
    public int getDest() {
        return (int) (nextVal & 0xffff_ffffL);
    }

    public void close() throws IOException {
        stream.close();
    }


}
