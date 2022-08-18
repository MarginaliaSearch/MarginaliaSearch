package nu.marginalia.wmsa.edge.crawling.retreival;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Cookies {
    final ThreadLocal<ConcurrentHashMap<String, List<Cookie>>> cookieJar = ThreadLocal.withInitial(ConcurrentHashMap::new);

    public CookieJar getJar() {
        return new CookieJar() {

            @Override
            public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {

                if (!cookies.isEmpty()) {
                    cookieJar.get().put(url.host(), cookies);
                }
            }

            @Override
            public List<Cookie> loadForRequest(HttpUrl url) {
                return cookieJar.get().getOrDefault(url.host(), Collections.emptyList());
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
