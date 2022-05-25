package nu.marginalia.util.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class TestUtil {
    private static boolean isTempDir(Path dir) {
        return dir.startsWith("/tmp") || dir.toString().contains("tmp");
    }
    public static void clearTempDir(Path dir) {
        if (!isTempDir(dir)) {
            throw new IllegalArgumentException("Refusing to recursively delete directory with that name");
        }
        if (Files.isDirectory(dir)) {
            for (File f : dir.toFile().listFiles()) {
                File[] files = f.listFiles();
                if (files != null) {
                    Arrays.stream(files).map(File::toPath).forEach(TestUtil::clearTempDir);
                }
                System.out.println("Deleting " + f);
                f.delete();
            }
        }
        System.out.println("Deleting " + dir);
        dir.toFile().delete();
    }
}
