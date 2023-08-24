package nu.marginalia.linkdb;

import nu.marginalia.linkdb.model.UrlStatus;
import nu.marginalia.model.EdgeUrl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;

public class LinkdbStatusWriterTest {
    @Test
    public void testCreate() throws IOException {
        Path tempPath = Files.createTempFile("linkdb-status", ".db");
        try {
            var writer = new LinkdbStatusWriter(tempPath);
            writer.add(List.of(
                    new UrlStatus(5, new EdgeUrl("https://www.marginalia.nu/x"), "y", null),
                    new UrlStatus(6, new EdgeUrl("https://www.marginalia.nu/y"), "y", "z")
                    ));
            writer.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }
}
