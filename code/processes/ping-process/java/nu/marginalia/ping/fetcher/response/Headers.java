package nu.marginalia.ping.fetcher.response;

import java.util.List;
import java.util.Map;

public record Headers(Map<String, List<String>> headers) {
    public List<String> get(String name) {
        return headers.getOrDefault(name, List.of());
    }

    public String getFirst(String name) {
        return headers.getOrDefault(name, List.of()).stream().findFirst().orElse(null);
    }

    public boolean contains(String name) {
        return headers.containsKey(name);
    }
}
