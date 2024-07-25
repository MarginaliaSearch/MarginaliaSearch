package nu.marginalia.slop.column;

import nu.marginalia.slop.desc.ColumnDesc;
import nu.marginalia.slop.desc.ColumnFunction;
import nu.marginalia.slop.desc.ColumnType;
import nu.marginalia.slop.desc.StorageType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StringColumnTest {
    Path tempDir;

    @BeforeEach
    void setup() throws IOException {
        tempDir = Files.createTempDirectory(getClass().getSimpleName());
    }

    @AfterEach
    void cleanup() {
        try {
            Files.walk(tempDir)
                    .sorted(this::deleteOrder)
                    .forEach(p -> {
                        try {
                            if (Files.isRegularFile(p)) {
                                System.out.println("Deleting " + p + " " + Files.size(p));
                            }
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    int deleteOrder(Path a, Path b) {
        if (Files.isDirectory(a) && !Files.isDirectory(b)) {
            return 1;
        } else if (!Files.isDirectory(a) && Files.isDirectory(b)) {
            return -1;
        } else {
            return a.getNameCount() - b.getNameCount();
        }
    }

    @Test
    void testArrayStr() throws IOException {
        var name = new ColumnDesc<>("test",
                0,
                ColumnFunction.DATA,
                ColumnType.STRING,
                StorageType.GZIP);

        try (var column = name.create(tempDir)) {
            column.put("Lorem");
            column.put("Ipsum");
        }
        try (var column = name.open(tempDir)) {
            assertEquals("Lorem", column.get());
            assertEquals("Ipsum", column.get());
            assertFalse(column.hasRemaining());
        }
    }

    @Test
    void testCStr() throws IOException {
        var name = new ColumnDesc<>("test",
                0,
                ColumnFunction.DATA,
                ColumnType.CSTRING,
                StorageType.GZIP);

        try (var column = name.create(tempDir)) {
            column.put("Lorem");
            column.put("Ipsum");
        }
        try (var column = name.open(tempDir)) {
            assertEquals("Lorem", column.get());
            assertEquals("Ipsum", column.get());
            assertFalse(column.hasRemaining());
        }
    }

    @Test
    void testTxtStr() throws IOException {
        var name = new ColumnDesc<>("test",
                0,
                ColumnFunction.DATA,
                ColumnType.TXTSTRING,
                StorageType.GZIP);

        try (var column = name.create(tempDir)) {
            column.put("Lorem");
            column.put("Ipsum");
        }
        try (var column = name.open(tempDir)) {
            assertEquals("Lorem", column.get());
            assertEquals("Ipsum", column.get());
            assertFalse(column.hasRemaining());
        }
    }
}