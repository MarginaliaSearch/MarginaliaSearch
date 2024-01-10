package nu.marginalia.linkdb;

import nu.marginalia.linkdb.dlinks.DomainLinkDbLoader;
import nu.marginalia.linkdb.dlinks.DomainLinkDbWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DomainLinkDbTest {
    Path fileName;
    @BeforeEach
    public void setUp() throws IOException {
        fileName = Files.createTempFile("test", ".db");
    }
    @AfterEach
    public void tearDown() throws IOException {
        Files.deleteIfExists(fileName);
    }

    @Test
    public void testWriteRead() {
        try (var writer = new DomainLinkDbWriter(fileName)) {
            writer.write(1, 2);
            writer.write(2, 3);
            writer.write(3, 4);
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        try (var reader = new DomainLinkDbLoader(fileName)) {
            Assertions.assertTrue(reader.next());
            Assertions.assertEquals(1, reader.getSource());
            Assertions.assertEquals(2, reader.getDest());
            Assertions.assertTrue(reader.next());
            Assertions.assertEquals(2, reader.getSource());
            Assertions.assertEquals(3, reader.getDest());
            Assertions.assertTrue(reader.next());
            Assertions.assertEquals(3, reader.getSource());
            Assertions.assertEquals(4, reader.getDest());
            Assertions.assertFalse(reader.next());
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
