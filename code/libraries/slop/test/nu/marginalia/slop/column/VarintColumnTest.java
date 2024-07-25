package nu.marginalia.slop.column;

import nu.marginalia.slop.column.dynamic.VarintColumn;
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

class VarintColumnTest {
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
    void test() throws IOException {
        var name = new ColumnDesc("test",
                0,
                ColumnFunction.DATA,
                ColumnType.VARINT_LE,
                StorageType.PLAIN);

        try (var column = VarintColumn.create(tempDir, name)) {
            column.put(42);
            column.put(43);
            column.put(65534);
            column.put(1);
            column.put(0);
            column.put(6000000000L);
            column.put(1);
        }
        try (var column = VarintColumn.open(tempDir, name)) {
            assertEquals(42, column.get());
            assertEquals(43, column.get());
            assertEquals(65534, column.get());
            assertEquals(1, column.get());
            assertEquals(0, column.get());
            assertEquals(6000000000L, column.get());
            assertEquals(1, column.get());
        }
    }

    @Test
    void test22() throws IOException {
        var name = new ColumnDesc("test",
                0,
                ColumnFunction.DATA,
                ColumnType.VARINT_LE,
                StorageType.PLAIN);

        try (var column = VarintColumn.create(tempDir, name)) {
            column.put(2);
            column.put(2);
        }
        try (var column = VarintColumn.open(tempDir, name)) {
            assertEquals(2, column.get());
            assertEquals(2, column.get());
        }
    }

}