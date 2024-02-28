package nu.marginalia.contenttype;

import org.apache.commons.lang3.StringUtils;

/** Content type and charset of a document
 * @param contentType The content type, e.g. "text/html"
 * @param charset The charset, e.g. "UTF-8"
 */
public record ContentType(String contentType, String charset) {
    public static ContentType parse(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank())
            return new ContentType(null,  null);

        String[] parts = StringUtils.split(contentTypeHeader, ";", 2);
        String contentType = parts[0].trim();
        String charset = parts.length > 1 ? parts[1].trim() : "UTF-8";

        return new ContentType(contentType, charset);
    }

    public boolean is(String contentType) {
        return this.contentType.equalsIgnoreCase(contentType);
    }

    public String toString() {
        if (charset == null || charset.isBlank())
            return contentType;

        return STR."\{contentType}; charset=\{charset}";
    }
}
