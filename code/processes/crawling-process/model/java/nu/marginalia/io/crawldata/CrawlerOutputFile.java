package nu.marginalia.io.crawldata;

import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CrawlerOutputFile {

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

    public static Path createWarcPath(Path basePath, String id, String domain, WarcFileVersion version) throws IOException {
        id = padId(id);

        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = basePath.resolve(first).resolve(second);
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        return destDir.resolve(STR."\{id}-\{filesystemSafeName(domain)}-\{version.suffix}.warc.gz");
    }

    public static Path createParquetPath(Path basePath, String id, String domain) throws IOException {
        id = padId(id);

        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = basePath.resolve(first).resolve(second);
        if (!Files.exists(destDir)) {
            Files.createDirectories(destDir);
        }
        return destDir.resolve(STR."\{id}-\{filesystemSafeName(domain)}.parquet");
    }
    public static Path getParquetPath(Path basePath, String id, String domain) {
        id = padId(id);

        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = basePath.resolve(first).resolve(second);
        return destDir.resolve(STR."\{id}-\{filesystemSafeName(domain)}.parquet");
    }
    public static Path getWarcPath(Path basePath, String id, String domain, WarcFileVersion version) {
        id = padId(id);

        String first = id.substring(0, 2);
        String second = id.substring(2, 4);

        Path destDir = basePath.resolve(first).resolve(second);
        return destDir.resolve(STR."\{id}-\{filesystemSafeName(domain)}.warc\{version.suffix}");
    }

    /**
     * Pads the given ID with leading zeros to ensure it has a length of 4 characters.
     */
    private static String padId(String id) {
        if (id.length() < 4) {
            id = Strings.repeat("0", 4 - id.length()) + id;
        }

        return id;
    }


    public enum WarcFileVersion {
        LIVE("open"),
        TEMP("tmp");

        public final String suffix;

        WarcFileVersion(String suffix) {
            this.suffix = suffix;
        }
    }
}
