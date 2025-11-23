package nu.marginalia.functions.searchquery.searchfilter;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.floats.FloatList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.query.QueryStrategy;
import nu.marginalia.api.searchquery.model.query.SpecificationLimit;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.api.searchquery.model.CompiledSearchFilterSpec;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import static nu.marginalia.api.searchquery.model.SearchFilterDefaults.SYSTEM_DEFAULT_FILTER;
import static nu.marginalia.api.searchquery.model.SearchFilterDefaults.SYSTEM_USER_ID;

@Singleton
public class SearchFilterCache {

    record SearchFilterKey(String user, String identifier) {}

    private final LoadingCache<SearchFilterKey, CompiledSearchFilterSpec> compiledSpecCache = CacheBuilder
            .newBuilder()
            .refreshAfterWrite(Duration.ofHours(1))
            .expireAfterAccess(Duration.ofHours(3))
            .maximumSize(1000)
            .build(new CacheLoader<>() {
                @Override
                public CompiledSearchFilterSpec load(@NotNull SearchFilterKey key) throws NoSuchElementException {
                    if (SYSTEM_USER_ID.equals(key.user) && SYSTEM_DEFAULT_FILTER.equals(key.identifier))
                    {
                        return CompiledSearchFilterSpec.builder(SYSTEM_USER_ID, SYSTEM_DEFAULT_FILTER).build();
                    }

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

    public void invalidate(String userId, String filterId) {
        compiledSpecCache.refresh(new SearchFilterKey(userId, filterId));
    }

}
