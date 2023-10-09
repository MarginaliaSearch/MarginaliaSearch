package nu.marginalia.api.svc;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Singleton;
import nu.marginalia.api.model.*;

import java.time.Duration;
import java.util.Optional;

/** This response cache exists entirely to help clients with its rate limiting.
 */
@Singleton
public class ResponseCache {
    private final Cache<String, ApiSearchResults> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .expireAfterAccess(Duration.ofSeconds(30))
            .maximumSize(128)
            .build();

    public Optional<ApiSearchResults> getResults(ApiLicense license, String queryString, String queryParams) {
        return Optional.ofNullable(
                cache.getIfPresent(getCacheKey(license, queryString, queryParams))
        );
    }

    public void putResults(ApiLicense license, String queryString, String queryParams, ApiSearchResults results) {
        cache.put(getCacheKey(license, queryString, queryParams), results);
    }

    private String getCacheKey(ApiLicense license, String queryString, String queryParams) {
        return license.getKey() + ":" +  queryString + ":" + queryParams;
    }

    public void flush() {
        cache.invalidateAll();
    }

    public void cleanUp() {
        cache.cleanUp();
    }
}
