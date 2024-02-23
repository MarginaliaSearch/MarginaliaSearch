package nu.marginalia.encyclopedia;

import nu.marginalia.encyclopedia.cleaner.WikiCleaner;
import nu.marginalia.encyclopedia.store.ArticleDbProvider;
import nu.marginalia.encyclopedia.store.ArticleStoreWriter;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

/** Converts an OpenZim file with Wikipedia articles to a SQLite database
 * with cleaned-up MediaWiki HTML
 */
public class EncyclopediaConverter {
    private static final Logger logger = LoggerFactory.getLogger(EncyclopediaConverter.class);

    public static void convert(Path inputFile, Path outputFile) throws IOException, SQLException, InterruptedException {
        var wc = new WikiCleaner();

        try (var executor = Executors.newWorkStealingPool(Math.clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 32))) {

            var size = new AtomicInteger();

            if (!Files.exists(inputFile)) {
                throw new IllegalStateException("ZIM file not found: " + inputFile);
            }
            Files.deleteIfExists(outputFile);

            try (var asw = new ArticleStoreWriter(new ArticleDbProvider(outputFile))) {
                Predicate<Integer> keepGoing = (s) -> true;

                BiConsumer<String, String> handleArticle = (url, html) -> {
                    if (executor.isTerminated())
                        return;

                    executor.submit(() -> {
                        int sz = size.incrementAndGet();
                        if (sz % 1000 == 0) {
                            System.out.printf("\u001b[2K\r%d", sz);
                        }
                        asw.add(wc.cleanWikiJunk(url, html));
                    });

                    size.incrementAndGet();
                };

                new ZIMReader(new ZIMFile(inputFile.toString())).forEachArticles(handleArticle, keepGoing);
            }
        }
    }
}
