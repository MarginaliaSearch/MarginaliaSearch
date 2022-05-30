package nu.marginalia.wmsa.edge.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.search.command.SearchCommandInterface;
import nu.marginalia.wmsa.edge.search.command.SearchParameters;
import nu.marginalia.wmsa.edge.search.exceptions.RedirectException;
import nu.marginalia.wmsa.edge.search.model.BrowseResultSet;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

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
            String key = entry.getKey();
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
