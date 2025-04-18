package nu.marginalia.crawl.fetcher;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;

public class DomainCookies {
    private final Map<String, String> cookies = new HashMap<>();

    public boolean hasCookies() {
        return !cookies.isEmpty();
    }

    public void updateCookieStore(HttpResponse response) {
        for (var header : response.getHeaders()) {
            if (header.getName().equalsIgnoreCase("Set-Cookie")) {
                parseCookieHeader(header.getValue());
            }
        }
    }

    private void parseCookieHeader(String value) {
        // Parse the Set-Cookie header value and extract the cookies

        String[] parts = value.split(";");
        String cookie = parts[0].trim();

        if (cookie.contains("=")) {
            String[] cookieParts = cookie.split("=");
            String name = cookieParts[0].trim();
            String val = cookieParts[1].trim();
            cookies.put(name, val);
        }
    }

    public void paintRequest(HttpUriRequestBase request) {
        request.addHeader("Cookie", createCookieHeader());
    }

    public void paintRequest(ClassicHttpRequest request) {
        request.addHeader("Cookie", createCookieHeader());
    }

    private String createCookieHeader() {
        StringJoiner sj = new StringJoiner("; ");
        for (var cookie : cookies.entrySet()) {
            sj.add(cookie.getKey() + "=" + cookie.getValue());
        }
        return sj.toString();
    }

}
