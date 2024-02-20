package nu.marginalia.sideload;

import nu.marginalia.integration.reddit.db.RedditDb;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/** Contains helper functions for pre-converting stackexchange style 7z
 * files to marginalia-digestible sqlite databases*/
public class RedditSideloadHelper {
    private static final Logger logger = LoggerFactory.getLogger(RedditSideloadHelper.class);

    /** Looks for stackexchange 7z files in the given path and converts them to sqlite databases.
     *  The function is idempotent, so it is safe to call it multiple times on the same path
     *  (it will not re-convert files that have already been successfully converted)
     * */
    public static void convertRedditData(Path sourcePath) {
        if (!Files.isDirectory(sourcePath)) {
            throw new UnsupportedOperationException("RedditSideloadHelper.convertRedditData only supports directories");
        }

        Set<String> allFileNames;
        try (var contents = Files.list(sourcePath)) {
            allFileNames = contents.filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .map(File::getName)
                    .filter(name -> name.endsWith(".zst"))
                    .sorted()
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException ex) {
            logger.warn("Failed to convert reddit zstd file to sqlite database", ex);
            return;
        }

        int parallelism = Math.clamp(ForkJoinPool.getCommonPoolParallelism(), 1, Runtime.getRuntime().availableProcessors() / 2);
        try (var executor = Executors.newWorkStealingPool(parallelism))
        {
            for (var fileName : allFileNames) {
                if (!fileName.endsWith(RedditFilePair.submissionsSuffix)) {
                    continue;
                }

                String baseName = fileName.substring(0, fileName.length() - RedditFilePair.submissionsSuffix.length());
                String commentsFileName = baseName + RedditFilePair.commentsSuffix;

                if (!allFileNames.contains(commentsFileName)) {
                    logger.warn("Skipping reddit file pair {} because it is missing the comments file", fileName);
                    return;
                }

                executor.submit(() -> convertSingleRedditFile(new RedditFilePair(sourcePath, baseName)));
            }
        }
    }

    record RedditFilePair(Path rootDir, String fileNameBase) {
        static String submissionsSuffix = "_submissions.zst";
        static String commentsSuffix = "_comments.zst";

        public String submissionsFileName() { return fileNameBase + submissionsSuffix; }
        public String commentsFileName() { return fileNameBase + commentsSuffix; }

        public Path submissionsPath() { return rootDir.resolve(submissionsFileName()); }
        public Path commentsPath() { return rootDir.resolve(commentsFileName()); }
    }

    private static void convertSingleRedditFile(RedditFilePair files) {
        try {
            Path destPath = getRedditDbPath(files);
            if (Files.exists(destPath)) // already converted
                return;

            Path tempFile = Files.createTempFile(destPath.getParent(), "processed", "db.tmp");
            try {
                logger.info("Converting reddit zstd file {} to sqlite database", files.fileNameBase);
                RedditDb.create(files.submissionsPath(), files.commentsPath(), tempFile);
                logger.info("Finished converting reddit zstd file {} to sqlite database", files.fileNameBase);
                Files.move(tempFile, destPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                logger.error("Failed to convert reddit zstd file to sqlite database", e);
                Files.deleteIfExists(tempFile);
                Files.deleteIfExists(destPath);
            }
        } catch (IOException ex) {
            logger.warn("Failed to convert reddit zstd file to sqlite database", ex);
        }
    }

    private static Path getRedditDbPath(RedditFilePair pair) throws IOException {
        String hash = SideloadHelper.getCrc32FileHash(pair.commentsPath());
        return pair.rootDir().resolve(STR."\{pair.fileNameBase}.\{hash}.db");
    }

}