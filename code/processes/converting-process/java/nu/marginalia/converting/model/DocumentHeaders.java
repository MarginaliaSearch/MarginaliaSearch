package nu.marginalia.converting.model;

import java.util.*;
import java.util.regex.Pattern;

/** Encapsulates the HTTP headers of a document.
 */
public class DocumentHeaders {
    public final String raw;

    private final Map<String, List<String>> headers = new HashMap<>();

    private static final Pattern NEWLINE_PATTERN = Pattern.compile("(\r?\n)+");

    public DocumentHeaders(String raw) {
        this.raw = Objects.requireNonNullElse(raw, "");

        for (var line : eachLine()) {
            int colonIndex = line.indexOf(':');

            if (colonIndex == -1) continue;

            String key = line.substring(0, colonIndex).trim().toLowerCase();
            String value = line.substring(colonIndex + 1).trim();

            headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
    }

    public List<String> get(String key) {
        return headers.getOrDefault(key.toLowerCase(), List.of());
    }

    public List<String> eachLine() {
        if (raw.isBlank())
            return List.of();

        return List.of(NEWLINE_PATTERN.split(raw));
    }

    public List<String> eachLineLowercase() {
        if (raw.isBlank())
            return List.of();

        return List.of(NEWLINE_PATTERN.split(raw.toLowerCase()));
    }

    public boolean contains(String key) {
        return headers.containsKey(key.toLowerCase());
    }
    public boolean contains(String key, String value) {
        return headers.getOrDefault(key.toLowerCase(), List.of()).contains(value);
    }
    public boolean containsIgnoreCase(String key, String value) {
        return headers.getOrDefault(key.toLowerCase(), List.of())
                .stream()
                .map(String::toLowerCase)
                .anyMatch(s -> s.equals(value.toLowerCase()));
    }
}
