package nu.marginalia.slop.column;

import nu.marginalia.slop.column.string.EnumColumn;
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

class EnumColumnTest {
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

    Path tempFile() {
        try {
            return Files.createTempFile(tempDir, getClass().getSimpleName(), ".dat");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void test() throws IOException {
        var name = new ColumnDesc("test",
                0,
                ColumnFunction.DATA,
                ColumnType.ENUM_BE,
                StorageType.PLAIN);

        try (var column = EnumColumn.create(tempDir, name)) {
            column.put("Foo");
            column.put("Bar");
            column.put("Baz");
            column.put("Foo");
            column.put("Foo");
            column.put("Bar");
            column.put("Baz");
        }

        try (var column = EnumColumn.open(tempDir, name)) {
            assertEquals("Foo", column.get());
            assertEquals("Bar", column.get());
            assertEquals("Baz", column.get());
            assertEquals("Foo", column.get());
            assertEquals("Foo", column.get());
            assertEquals("Bar", column.get());
            assertEquals("Baz", column.get());
        }
    }

}