package org.netpreserve.jwarc;

import java.io.IOException;
import java.net.URI;

/** This defines a non-standard extension to WARC for storing old HTTP responses,
 * essentially a 'response' with different semantics
 */
public class WarcXEntityRefused extends WarcRevisit {
    private static final String TYPE_NAME = "x-entity-refused";

    public static final URI documentRobotsTxtSkippedURN = URI.create("urn:marginalia/meta/doc/robots-txt-skipped");
    public static final URI documentBadContentTypeURN = URI.create("urn:marginalia/meta/doc/content-type-failed-probe");
    public static final URI documentProbeTimeout = URI.create("urn:marginalia/meta/doc/timeout-probe");
    public static final URI documentUnspecifiedError = URI.create("urn:marginalia/meta/doc/error");

    WarcXEntityRefused(MessageVersion version, MessageHeaders headers, MessageBody body) {
        super(version, headers, body);
    }

    public static void register(WarcReader reader) {
        reader.registerType(TYPE_NAME, WarcXEntityRefused::new);
    }

    public static class Builder extends AbstractBuilder<WarcXEntityRefused, Builder> {
        public Builder(URI targetURI, URI profile) {
            this(targetURI.toString(), profile.toString());
        }

        public Builder(String targetURI, String profileURI) {
            super(TYPE_NAME);
            setHeader("WARC-Target-URI", targetURI);
            setHeader("WARC-Profile", profileURI);
        }

        public Builder body(HttpResponse httpResponse) throws IOException {
            return body(MediaType.HTTP_RESPONSE, httpResponse);
        }

        @Override
        public WarcXEntityRefused build() {
            return build(WarcXEntityRefused::new);
        }
    }
}
