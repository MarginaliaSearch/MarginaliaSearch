package nu.marginalia.ranking.connectivity;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** On-disk representation of the connectivity map, a sequence of (domain id, connectivity code)
 * pairs.  The file is written by the ranking constructor process, and read by the index service.
 */
public class ConnectivityMapFile {
    public static final String FILE_NAME = "__CONNECTIVITY_MAP";

    public static Int2ByteOpenHashMap read(Path filePath) throws IOException {
        Int2ByteOpenHashMap connectivity = new Int2ByteOpenHashMap();

        try (var ds = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(filePath, StandardOpenOption.READ))
        )) {
            for (;;) {
                int id = ds.readInt();
                byte code = ds.readByte();

                connectivity.put(id, code);
            }
        }
        catch (EOFException ex) {
            // expected end of data
        }

        return connectivity;
    }

    public static void write(Path filePath, Int2ByteOpenHashMap connectivity) throws IOException {
        try (var ds = new DataOutputStream(
                new BufferedOutputStream(
                Files.newOutputStream(filePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))
        ))
        {
            for (var entry : connectivity.int2ByteEntrySet()) {
                ds.writeInt(entry.getIntKey());
                ds.writeByte(entry.getByteValue());
            }
        }
    }
}
