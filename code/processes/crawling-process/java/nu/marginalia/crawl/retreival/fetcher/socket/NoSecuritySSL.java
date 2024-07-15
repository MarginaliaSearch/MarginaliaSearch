package nu.marginalia.crawl.retreival.fetcher.socket;

import lombok.SneakyThrows;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class NoSecuritySSL {

    // Create a trust manager that does not validate certificate chains
    // We want to accept e.g. self-signed certificates and certificates
    // that are not signed by a CA is generally trusted by the system.
    public static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain,
                                               String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                               String authType) {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };

    @SneakyThrows
    public static SSLSocketFactory buildSocketFactory() {
        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        var clientSessionContext = sslContext.getClientSessionContext();

        // The default value for this is very high and will use a crapload of memory
        // since the crawler will be making a lot of requests to various hosts
        clientSessionContext.setSessionCacheSize(2048);

        // Create a ssl socket factory with our all-trusting manager
        return sslContext.getSocketFactory();
    }

    public static HostnameVerifier buildHostnameVerifyer() {
        return (hn, session) -> true;
    }
}
