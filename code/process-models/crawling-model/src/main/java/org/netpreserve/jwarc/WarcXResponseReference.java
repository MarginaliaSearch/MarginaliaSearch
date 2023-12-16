package org.netpreserve.jwarc;

import java.io.IOException;
import java.net.URI;

/** This defines a non-standard extension to WARC for storing old HTTP responses,
 * essentially a 'response' with different semantics..
 * <p>
 * An x-response-reference record is a response record with a full body, where
 * the data is a reconstructed HTTP response from a previous crawl.
 */
public class WarcXResponseReference extends WarcResponse {
    private static final String TYPE_NAME = "x-response-reference";

    WarcXResponseReference(MessageVersion version, MessageHeaders headers, MessageBody body) {
        super(version, headers, body);
    }

    public static void register(WarcReader reader) {
        reader.registerType(TYPE_NAME, WarcXResponseReference::new);
    }

    public static class Builder extends AbstractBuilder<WarcXResponseReference, Builder> {
        public Builder(URI targetURI) {
            this(targetURI.toString());
        }

        public Builder(String targetURI) {
            super(TYPE_NAME);
            setHeader("WARC-Target-URI", targetURI);
        }

        public Builder body(HttpResponse httpResponse) throws IOException {
            return body(MediaType.HTTP_RESPONSE, httpResponse);
        }

        @Override
        public WarcXResponseReference build() {
            return build(WarcXResponseReference::new);
        }
    }
}
