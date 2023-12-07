package nu.marginalia.contenttype;

/** Content type and charset of a document
 * @param contentType The content type, e.g. "text/html"
 * @param charset The charset, e.g. "UTF-8"
 */
public record ContentType(String contentType, String charset) {

}
