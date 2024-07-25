package nu.marginalia.slop.column;

import nu.marginalia.slop.column.array.IntArrayColumn;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ArrayColumnTest {
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
                ColumnType.INT_ARRAY_LE,
                StorageType.PLAIN
        );


        try (var column = IntArrayColumn.create(tempDir, name)) {
            column.put(new int[] { 11, 22, 33});
            column.put(new int[] { 2 });
            column.put(new int[] { 444 });
        }
        try (var column = IntArrayColumn.open(tempDir, name)) {
            assertArrayEquals(new int[] { 11, 22, 33}, column.get());
            assertArrayEquals(new int[] { 2 }, column.get());
            assertArrayEquals(new int[] { 444 }, column.get());
        }
    }

}