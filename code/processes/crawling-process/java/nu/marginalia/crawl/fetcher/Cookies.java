package nu.marginalia.crawl.fetcher;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Cookies extends CookieHandler {
    final ThreadLocal<ConcurrentHashMap<String, List<String>>> cookieJar = ThreadLocal.withInitial(ConcurrentHashMap::new);

    public void clear() {
        cookieJar.get().clear();
    }

    public boolean hasCookies() {
        return !cookieJar.get().isEmpty();
    }

    public List<String> getCookies() {
        return cookieJar.get().values().stream().flatMap(List::stream).toList();
    }

    @Override
    public Map<String, List<String>> get(URI uri, Map<String, List<String>> requestHeaders) throws IOException {
        return cookieJar.get();
    }

    @Override
    public void put(URI uri, Map<String, List<String>> responseHeaders) throws IOException {
        cookieJar.get().putAll(responseHeaders);
    }
}
