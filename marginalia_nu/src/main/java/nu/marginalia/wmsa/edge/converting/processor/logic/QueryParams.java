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
        if (path.endsWith("index.php")) {
            if (param.startsWith("showtopic"))
                return true;
            if (param.startsWith("showforum"))
                return true;
        }
        if (path.endsWith("viewtopic.php")) {
            return (param.startsWith("t=") || param.startsWith("p="));
        }
        if (path.endsWith("viewforum.php")) {
            return param.startsWith("v=");
        }
        if (path.endsWith("showthread.php")) {
            return (param.startsWith("t=") || param.startsWith("p="));
        }
        if (path.endsWith("showforum.php")) {
            return param.startsWith("v=");
        }
        return false;
    }
}
