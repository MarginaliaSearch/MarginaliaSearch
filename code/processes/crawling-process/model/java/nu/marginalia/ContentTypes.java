package nu.marginalia;

import org.apache.commons.lang3.StringUtils;

import java.util.Set;

public class ContentTypes {
    public static final Set<String> acceptedContentTypes = Set.of("application/xhtml+xml",
            "application/xhtml",
            "text/html",
            "text/markdown",
            "text/x-markdown",
            "application/pdf",
            "image/x-icon",
            "text/plain");

    public static boolean isAccepted(String contentTypeHeader) {
        String lcHeader = StringUtils.substringBefore(contentTypeHeader.toLowerCase(), ';');
        for (var type : acceptedContentTypes) {
            if (lcHeader.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBinary(String contentTypeHeader) {
        String lcHeader = StringUtils.substringBefore(contentTypeHeader.toLowerCase(), ';');
        return lcHeader.startsWith("application/pdf");
    }

}
