package nu.marginalia.wmsa.edge.index.service.dictionary;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.SneakyThrows;

import java.util.concurrent.TimeUnit;

@Singleton
public class DictionaryReader {
    private final DictionaryWriter writer;

    private final Cache<String, Integer> cache = CacheBuilder.newBuilder().maximumSize(10_000).expireAfterAccess(60, TimeUnit.SECONDS).build();

    @SneakyThrows @Inject
    public DictionaryReader(DictionaryWriter writer) {
        this.writer = writer;
    }

    @SneakyThrows
    public int get(String word) {
        return cache.get(word, () -> writer.getReadOnly(word));
    }

}
