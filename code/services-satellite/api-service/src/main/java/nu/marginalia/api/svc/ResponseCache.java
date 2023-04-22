package nu.marginalia.api.svc;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Singleton;
import nu.marginalia.api.model.ApiLicense;
import nu.marginalia.search.client.model.ApiSearchResults;

import java.time.Duration;
import java.util.Optional;

/** This response cache exists entirely to help SearXNG with its rate limiting.
 * For some reason they're hitting the API with like 5-12 identical requests.
 * <p/>
 * I've submitted an issue, they were like nah mang it works fine must
 * be something else  ¯\_(ツ)_/¯.
 * <p/>
 * So we're going to cache the API responses for a short while to mitigate the
 * impact of such shotgun queries on the ratelimit.
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
}
