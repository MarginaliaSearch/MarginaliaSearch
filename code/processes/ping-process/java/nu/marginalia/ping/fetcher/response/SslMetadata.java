package nu.marginalia.ping.fetcher.response;

import javax.net.ssl.SSLSession;

public record SslMetadata(
        String cipherSuite,
        String protocol) {
    public SslMetadata(SSLSession session) {
        this(
                session.getCipherSuite(),
                session.getProtocol()
        );
    }
}
