package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.client.Context;
import nu.marginalia.search.exceptions.RedirectException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BangCommand implements SearchCommandInterface {
    private final Map<String, String> bangsToPattern = new HashMap<>();

    @Inject
    public BangCommand()
    {
        bangsToPattern.put("!g", "https://www.google.com/search?q=%s");
        bangsToPattern.put("!ddg", "https://duckduckgo.com/search?q=%s");
    }

    @Override
    public Optional<Object> process(Context ctx, SearchParameters parameters, String query) {

        for (var entry : bangsToPattern.entrySet()) {
            matchBangPattern(query, entry.getKey(), entry.getValue());
        }

        return Optional.empty();
    }

    private void matchBangPattern(String query, String bangKey, String urlPattern) {
        for (int idx = query.indexOf(bangKey); idx >= 0; idx = query.indexOf(bangKey, idx + 1)) {

            if (idx > 0) { // Don't match "search term!b", require either "!b term" or "search term !b"
                if (!Character.isSpaceChar(query.charAt(idx-1))) {
                    continue;
                }
            }
            int nextIdx = idx + bangKey.length();

            if (nextIdx >= query.length()) { // allow "search term !b"
                redirect(urlPattern, query.substring(0, idx));
            }
            else if (Character.isSpaceChar(query.charAt(nextIdx))) { // skip matches on pattern "!bsearch term" for !b
                redirect(urlPattern, query.substring(0, idx).stripTrailing() + " " + query.substring(nextIdx).stripLeading());
            }
        }
    }

    private void redirect(String pattern, String terms) {
        var url = String.format(pattern, URLEncoder.encode(terms.trim(), StandardCharsets.UTF_8));
        throw new RedirectException(url);
    }
}
