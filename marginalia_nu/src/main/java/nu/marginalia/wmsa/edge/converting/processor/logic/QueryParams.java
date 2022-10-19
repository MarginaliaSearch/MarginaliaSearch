package nu.marginalia.wmsa.edge.converting.processor.logic;

import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

public class QueryParams {

    private static final Pattern paramSplitterPattern = Pattern.compile("&");

    @Nullable
    public static String queryParamsSanitizer(String path, @Nullable String queryParams) {
        if (queryParams == null) {
            return null;
        }

        String ret;
        if (queryParams.indexOf('&') >= 0) {

            List<String> parts = new ArrayList<>();
            for (var part : StringUtils.split(queryParams, '&')) {
                if (QueryParams.isPermittedParam(path, part)) {
                    parts.add(part);
                }
            }
            if (parts.size() > 1) {
                parts.sort(Comparator.naturalOrder());
            }
            StringJoiner retJoiner = new StringJoiner("&");
            parts.forEach(retJoiner::add);
            ret = retJoiner.toString();
        }
        else if (isPermittedParam(path, queryParams)) {
            ret = queryParams;
        }
        else {
            return null;
        }

        if (ret.isBlank())
            return null;

        return ret;
    }

    public static boolean isPermittedParam(String path, String param) {
        if (path.endsWith(".cgi")) return true;

        if (path.endsWith("/posting.php")) return false;

        if (param.startsWith("id=")) return true;
        if (param.startsWith("p=")) {
            // Don't retain forum links with post-id:s, they're always non-canonical and eat up a lot of
            // crawling bandwidth

            if (path.endsWith("showthread.php") || path.endsWith("viewtopic.php")) {
                return false;
            }
            return true;
        }
        if (param.startsWith("f=")) {
            if (path.endsWith("showthread.php") || path.endsWith("viewtopic.php")) {
                return false;
            }
            return true;
        }
        if (param.startsWith("i=")) return true;
        if (param.startsWith("start=")) return true;
        if (param.startsWith("t=")) return true;
        if (param.startsWith("v=")) return true;

        if (param.startsWith("post=")) return true;

        if (path.endsWith("index.php")) {
            if (param.startsWith("showtopic="))
                return true;
            if (param.startsWith("showforum="))
                return true;
        }

        if (path.endsWith("StoryView.py")) { // folklore.org is neat
            return param.startsWith("project=") || param.startsWith("story=");
        }
        return false;
    }
}
