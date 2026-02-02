package nu.marginalia.linkdb;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.linkdb.docs.DocumentDbWriter;
import nu.marginalia.linkdb.model.DocdbUrlDetail;
import nu.marginalia.model.EdgeDomain;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public class DocumentDbWriterTest {
    @Test
    public void testCreate() throws IOException {
        Path tempPath = Files.createTempFile("docdb", ".db");
        try {
            var writer = new DocumentDbWriter(tempPath);
            writer.add(new DocdbUrlDetail(
                    1,
                    new nu.marginalia.model.EdgeUrl("http", new EdgeDomain("example.com"), null, "/", null),
                    "Test",
                    "This is a test",
                    "en",
                    -4.,
                    "XHTML",
                    5,
                    2020,
                    0xF00BA3,
                    444
            ));
            writer.close();

            var reader = new DocumentDbReader(tempPath);
            var deets = reader.getUrlDetails(new LongArrayList(new long[]{1}));
            System.out.println(deets);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }
}
