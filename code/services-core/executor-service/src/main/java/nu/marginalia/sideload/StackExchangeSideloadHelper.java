package nu.marginalia.sideload;

import nu.marginalia.integration.stackexchange.sqlite.StackExchangePostsDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.zip.CRC32;

/** Contains helper functions for pre-converting stackexchange style 7z
 * files to marginalia-digestible sqlite databases*/
public class StackExchangeSideloadHelper {
    private static final Logger logger = LoggerFactory.getLogger(StackExchangeSideloadHelper.class);

    /** Looks for stackexchange 7z files in the given path and converts them to sqlite databases.
     *  The function is idempotent, so it is safe to call it multiple times on the same path
     *  (it will not re-convert files that have already been successfully converted)
     * */
    public static void convertStackexchangeData(Path sourcePath) {
        if (Files.isDirectory(sourcePath)) {
            try (var contents = Files.list(sourcePath)) {
                contents.filter(Files::isRegularFile)
                        .parallel()
                        .forEach(StackExchangeSideloadHelper::convertSingleStackexchangeFile);
            } catch (IOException ex) {
                logger.warn("Failed to convert stackexchange 7z file to sqlite database", ex);
            }
        } else if (Files.isRegularFile(sourcePath)) {
            convertSingleStackexchangeFile(sourcePath);
        }
    }

    private static void convertSingleStackexchangeFile(Path sourcePath) {
        String fileName = sourcePath.toFile().getName();

        if (fileName.endsWith(".db")) return;
        if (!fileName.endsWith(".7z")) return;

        Optional<String> domain = getStackexchangeDomainFromFilename(fileName);
        if (domain.isEmpty())
            return;

        try {
            Path destPath = getStackexchangeDbPath(sourcePath);
            if (Files.exists(destPath)) return;

            Path tempFile = Files.createTempFile(destPath.getParent(), "processed", "db.tmp");
            try {
                logger.info("Converting stackexchange 7z file {} to sqlite database", sourcePath);
                StackExchangePostsDb.create(domain.get(), tempFile, sourcePath);
                logger.info("Finished converting stackexchange 7z file {} to sqlite database", sourcePath);
                Files.move(tempFile, destPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                logger.error("Failed to convert stackexchange 7z file to sqlite database", e);
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(destPath);
            }
        } catch (IOException ex) {
            logger.warn("Failed to convert stackexchange 7z file to sqlite database", ex);
        }
    }

    private static Path getStackexchangeDbPath(Path sourcePath) throws IOException {
        String fileName = sourcePath.toFile().getName();
        String hash = SideloadHelper.getCrc32FileHash(sourcePath);

        return sourcePath.getParent().resolve(STR."\{fileName}.\{hash}.db");
    }

    private static Optional<String> getStackexchangeDomainFromFilename(String fileName) {
        // We are only interested in .tld.7z files
        if (!fileName.endsWith(".7z") || fileName.length() < 7)
            return Optional.empty();


        // Stackoverflow is special, because it has one 7z file per site
        // (we only want Posts)

        if (fileName.equals("stackoverflow.com-Posts.7z"))
            return Optional.of("www.stackoverflow.com");
        else if (fileName.startsWith("stackoverflow.com-")) {
            return Optional.empty();
        }

        // For stackexchange, we filter out the meta archives

        // We are not interested in the meta files
        if (fileName.startsWith("meta."))
            return Optional.empty();
        if (fileName.contains(".meta."))
            return Optional.empty();

        // Pattern is 'foobar.stackexchange.com.7z'
        return Optional.of(fileName.substring(0, fileName.length() - 3));
    }

}