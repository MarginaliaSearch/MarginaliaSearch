package nu.marginalia.search.command.commands;

import com.google.inject.Inject;
import nu.marginalia.search.command.SearchCommandInterface;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.client.Context;
import nu.marginalia.search.exceptions.RedirectException;
import spark.Response;

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
    public boolean process(Context ctx, Response response, SearchParameters parameters) {

        for (var entry : bangsToPattern.entrySet()) {
            String bangPattern = entry.getKey();
            String redirectPattern = entry.getValue();

            var match = matchBangPattern(parameters.query(), bangPattern);

            if (match.isPresent()) {
                var url = String.format(redirectPattern, URLEncoder.encode(match.get(), StandardCharsets.UTF_8));
                throw new RedirectException(url);
            }
        }

        return false;
    }

    private Optional<String> matchBangPattern(String query, String bangKey) {
        var bm = new BangMatcher(query);

        while (bm.findNext(bangKey)) {

            if (bm.isRelativeSpaceOrInvalid(-1))
                continue;
            if (bm.isRelativeSpaceOrInvalid(bangKey.length()))
                continue;

            String queryWithoutBang = bm.prefix().trim() + " " + bm.suffix(bangKey.length()).trim();
            return Optional.of(queryWithoutBang);
        }

        return Optional.empty();
    }

    private static class BangMatcher {
        private final String str;
        private int pos;

        public String prefix() {
            return str.substring(0, pos);
        }

        public String suffix(int offset) {
            if (pos+offset < str.length())
                return str.substring(pos + offset);
            return "";
        }

        public BangMatcher(String str) {
            this.str = str;
            this.pos = -1;
        }

        public boolean findNext(String pattern) {
            if (pos + 1 >= str.length())
                return false;

            return (pos = str.indexOf(pattern, pos + 1)) >= 0;
        }

        public boolean isRelativeSpaceOrInvalid(int offset) {
            if (offset + pos < 0)
                return true;
            if (offset + pos >= str.length())
                return true;

            return Character.isSpaceChar(str.charAt(offset + pos));
        }

    }

}
