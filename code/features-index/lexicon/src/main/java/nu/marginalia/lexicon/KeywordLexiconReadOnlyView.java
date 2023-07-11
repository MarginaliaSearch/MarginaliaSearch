package nu.marginalia.lexicon;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.SneakyThrows;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class KeywordLexiconReadOnlyView {
    private final KeywordLexicon writer;

    private final Cache<String, Integer> cache = CacheBuilder.newBuilder().maximumSize(10_000).expireAfterAccess(60, TimeUnit.SECONDS).build();

    @SneakyThrows
    public KeywordLexiconReadOnlyView(KeywordLexicon writer) {
        this.writer = writer;
    }

    @SneakyThrows
    public int get(String word) {
        return cache.get(word, () -> writer.getReadOnly(word));
    }

    public boolean reload() throws IOException {
        writer.reload();
        return true;
    }
}
