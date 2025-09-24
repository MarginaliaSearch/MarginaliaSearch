package nu.marginalia.test;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class TestUtil {
    public static void clearTempDir(Path path) {
        if (!Files.exists(path))
            return;

        if (Files.isDirectory(path)) {
            var contents = path.toFile().listFiles();

            for (File f : contents) {
                if (f.isDirectory()) {
                    File[] files = f.listFiles();
                    if (files != null) {
                        Arrays.stream(files).map(File::toPath).forEach(TestUtil::clearTempDir);
                    }
                }
                else {
                    f.delete();
                }
            }
        }
        path.toFile().delete();
    }

    private static String fileSize(Path path) {
        try {
            long sizeBytes = Files.size(path);

            if (sizeBytes > 1024 * 1024 * 1024) return round(sizeBytes / 1073741824.) + "Gb";
            if (sizeBytes > 1024 * 1024) return round(sizeBytes / 1048576.) + "Mb";
            if (sizeBytes > 1024) return round(sizeBytes / 1024.) + "Kb";
            return sizeBytes + "b";
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String round(double d) {
        return String.format("%.2f", d);
    }
}
