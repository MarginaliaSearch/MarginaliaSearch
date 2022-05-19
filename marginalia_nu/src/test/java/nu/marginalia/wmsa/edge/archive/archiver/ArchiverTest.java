package nu.marginalia.wmsa.edge.archive.archiver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.nio.file.Path;

public class ArchiverTest {

    @Test
    public void testArchiver() throws Exception {
        Archiver archiver = new Archiver(Path.of("/tmp/"), 3);
        archiver.writeData(new ArchivedFile("file1", "Hey".getBytes()));
        archiver.writeData(new ArchivedFile("file2", "Hey".getBytes()));
        archiver.writeData(new ArchivedFile("file3", "Hey".getBytes()));
        archiver.writeData(new ArchivedFile("file4", "Hey".getBytes()));
        archiver.close();
    }
}