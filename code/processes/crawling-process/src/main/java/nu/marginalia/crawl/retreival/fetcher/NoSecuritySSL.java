package nu.marginalia.crawl.retreival.fetcher;

import lombok.SneakyThrows;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class NoSecuritySSL {

    // Create a trust manager that does not validate certificate chains
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
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        var clientSessionContext = sslContext.getClientSessionContext();

        System.out.println("Default session cache size: " + clientSessionContext.getSessionCacheSize());
        System.out.println("Session timeout: " + clientSessionContext.getSessionTimeout());

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
