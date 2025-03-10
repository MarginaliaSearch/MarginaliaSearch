package nu.marginalia;

import java.util.Set;

public class ContentTypes {
    public static final Set<String> acceptedContentTypes = Set.of("application/xhtml+xml",
            "application/xhtml",
            "text/html",
            "image/x-icon",
            "text/plain");

    public static boolean isAccepted(String contentTypeHeader) {
        String lcHeader = contentTypeHeader.toLowerCase();
        for (var type : acceptedContentTypes) {
            if (lcHeader.startsWith(type)) {
                return true;
            }
        }
        return false;
    }

}
