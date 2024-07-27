package nu.marginalia.slop.column;

import nu.marginalia.slop.column.primitive.IntColumn;
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

import static org.junit.jupiter.api.Assertions.*;

class IntColumnTest {
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
                ColumnType.INT_LE,
                StorageType.PLAIN
        );

        try (var column = IntColumn.create(tempDir, name)) {
            column.put(42);
            column.put(43);
        }
        try (var column = IntColumn.open(tempDir, name)) {
            assertEquals(42, column.get());
            assertEquals(43, column.get());
        }
    }


    @Test
    void testLarge() throws IOException {
        var name = new ColumnDesc("test",
                0,
                ColumnFunction.DATA,
                ColumnType.INT_LE,
                StorageType.PLAIN
        );

        try (var column = IntColumn.create(tempDir, name)) {
            for (int i = 0; i < 64; i++) {
                column.put(i);
            }
        }
        try (var column = IntColumn.open(tempDir, name)) {
            int i = 0;
            while (column.hasRemaining()) {
                assertEquals(i++, column.get());
            }
            assertEquals(64, i);
        }
    }

    @Test
    void testLargeBulk() throws IOException {
        var name = new ColumnDesc("test",
                0,
                ColumnFunction.DATA,
                ColumnType.INT_LE,
                StorageType.PLAIN
        );


        int[] values = new int[24];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        try (var column = IntColumn.create(tempDir, name)) {
            column.put(values);
            column.put(values);
        }
        try (var column = IntColumn.open(tempDir, name)) {
            for (int i = 0; i < 2; i++) {
                for (int j = 0; j < values.length; j++) {
                    assertEquals(j, column.get());
                }
            }
            assertFalse(column.hasRemaining());
        }
    }

    @Test
    void testSkip() throws IOException {
        var name = new ColumnDesc("test",
                0,
                ColumnFunction.DATA,
                ColumnType.INT_LE,
                StorageType.PLAIN
        );


        int[] values = new int[24];
        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }
        try (var column = IntColumn.create(tempDir, name)) {
            column.put(values);
            column.put(values);
        }
        try (var column = IntColumn.open(tempDir, name)) {
            column.get();
            column.get();
            column.skip(34);
            assertEquals(12, column.get());

            assertTrue(column.hasRemaining());
        }
    }

}