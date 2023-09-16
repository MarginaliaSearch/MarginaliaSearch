package nu.marginalia.crawling.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawlerOutputFile {

    /** Return the Path to a file for the given id and name */
    public static Path getOutputFile(Path base, String id, String name) {
        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = base.resolve(first).resolve(second);
        return destDir.resolve(id + "-" + filesystemSafeName(name) + ".zstd");
    }

    /** Return the Path to a file for the given id and name, creating the prerequisite
     * directory structure as necessary. */
    public static Path createOutputPath(Path base, String id, String name) throws IOException {
        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = base.resolve(first).resolve(second);
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        return destDir.resolve(id + "-" + filesystemSafeName(name) + ".zstd");
    }


    private static String filesystemSafeName(String name) {
        StringBuilder nameSaneBuilder = new StringBuilder();

        name.chars()
                .map(Character::toLowerCase)
                .map(c -> (c & ~0x7F) == 0 ? c : 'X')
                .map(c -> (Character.isDigit(c) || Character.isAlphabetic(c) || c == '.') ? c : 'X')
                .limit(128)
                .forEach(c -> nameSaneBuilder.append((char) c));

        return nameSaneBuilder.toString();

    }

}
