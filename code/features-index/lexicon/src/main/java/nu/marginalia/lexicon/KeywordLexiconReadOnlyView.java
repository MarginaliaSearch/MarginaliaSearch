package nu.marginalia.lexicon;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/** A read-only view of a keyword lexicon.
 *
 * @see KeywordLexicon
 * */
public class KeywordLexiconReadOnlyView {
    private final KeywordLexicon writer;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Cache<String, Integer> cache = CacheBuilder.newBuilder().maximumSize(10_000).expireAfterAccess(60, TimeUnit.SECONDS).build();

    @SneakyThrows
    public KeywordLexiconReadOnlyView(KeywordLexicon writer) {
        this.writer = writer;
    }

    @SneakyThrows
    public int get(String word) {
        return cache.get(word, () -> writer.getReadOnly(word));
    }

    public boolean suggestReload() throws IOException {
        if (writer.needsReload()) {
            logger.info("Reloading lexicon");
            writer.reload();
            cache.invalidateAll();
        }
        else {
            logger.info("Foregoing lexicon reload");
        }
        return true;
    }
}
