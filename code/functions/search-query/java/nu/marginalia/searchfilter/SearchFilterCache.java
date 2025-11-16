package nu.marginalia.searchfilter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.searchfilter.model.CompiledSearchFilterSpec;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@Singleton
public class SearchFilterCache {
    record SearchFilterKey(String user, String identifier) {}

    private final LoadingCache<SearchFilterKey, CompiledSearchFilterSpec> compiledSpecCache = CacheBuilder
            .newBuilder()
            .refreshAfterWrite(Duration.ofHours(6))
            .expireAfterAccess(Duration.ofHours(3))
            .maximumSize(1000)
            .build(new CacheLoader<>() {
                @Override
                public CompiledSearchFilterSpec load(@NotNull SearchFilterKey key) throws Exception {
                    return store.getFilter(key.user(), key.identifier())
                            .map(spec -> spec.compile(dbQueries))
                            .orElseThrow(NoSuchElementException::new);
                }
            });

    private final SearchFilterStore store;
    private final DbDomainQueries dbQueries;

    @Inject
    public SearchFilterCache(SearchFilterStore store, DbDomainQueries dbQueries) {
        this.store = store;
        this.dbQueries = dbQueries;
    }

    public CompiledSearchFilterSpec get(String user, String identifier) throws ExecutionException {
        return compiledSpecCache.get(new SearchFilterKey(user, identifier));
    }
}
