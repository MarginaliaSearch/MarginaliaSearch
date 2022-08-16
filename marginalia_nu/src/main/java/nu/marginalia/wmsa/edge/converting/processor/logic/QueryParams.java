package nu.marginalia.wmsa.edge.converting.processor.logic;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QueryParams {

    private static final Pattern paramSplitterPattern = Pattern.compile("&");

    @Nullable
    public static String queryParamsSanitizer(String path, @Nullable String queryParams) {
        if (queryParams == null) {
            return null;
        }

        var ret = Arrays.stream(paramSplitterPattern.split(queryParams))
                .filter(param -> QueryParams.isPermittedParam(path, param))
                .sorted()
                .collect(Collectors.joining("&"));

        if (ret.isBlank())
            return null;

        return ret;
    }

    public static boolean isPermittedParam(String path, String param) {
        if (path.endsWith(".cgi")) return true;

        if (param.startsWith("id=")) return true;
        if (param.startsWith("p=")) return true;
        if (param.startsWith("i=")) return true;
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
