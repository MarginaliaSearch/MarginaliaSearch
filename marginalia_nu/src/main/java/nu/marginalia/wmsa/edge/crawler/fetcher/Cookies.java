package nu.marginalia.wmsa.edge.crawler.fetcher;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Cookies {
    final ThreadLocal<ConcurrentHashMap<HttpUrl, List<Cookie>>> cookieJar = ThreadLocal.withInitial(ConcurrentHashMap::new);

    public CookieJar getJar() {
        return new CookieJar() {

            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                if (!cookies.isEmpty()) {
                    cookieJar.get().put(url, cookies);
                }
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                return cookieJar.get().getOrDefault(url, Collections.emptyList());
            }
        };
    }

    public void clear() {
        cookieJar.get().clear();
    }

    public boolean hasCookies() {
        return !cookieJar.get().isEmpty();
    }

    public List<String> getCookies() {
        return cookieJar.get().values().stream().flatMap(List::stream).map(Cookie::toString).toList();
    }
}
