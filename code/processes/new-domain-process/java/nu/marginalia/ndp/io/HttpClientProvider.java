package nu.marginalia.ndp.io;

import com.google.inject.Provider;
import nu.marginalia.proxy.SocksProxyConfiguration;
import nu.marginalia.proxy.SocksProxyManager;
import org.apache.hc.client5.http.ConnectionKeepAliveStrategy;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.StandardCookieSpec;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HeaderElements;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.message.MessageSupport;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

public class HttpClientProvider implements Provider<HttpClient> {
    private static final HttpClient client;
    private static PoolingHttpClientConnectionManager connectionManager;
    private static final SocksProxyManager proxyManager;

    private static final Logger logger = LoggerFactory.getLogger(HttpClientProvider.class);

    static {
        proxyManager = new SocksProxyManager(new SocksProxyConfiguration());
        try {
            client = createClient();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static CloseableHttpClient createClient() throws NoSuchAlgorithmException, KeyManagementException {
        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setSocketTimeout(15, TimeUnit.SECONDS)
                .setConnectTimeout(15, TimeUnit.SECONDS)
                .setValidateAfterInactivity(TimeValue.ofSeconds(5))
                .build();

        PoolingHttpClientConnectionManagerBuilder connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnPerRoute(2)
                .setMaxConnTotal(50)
                .setDefaultConnectionConfig(connectionConfig);

        connectionManagerBuilder.setSocketConfigResolver(route -> {
            SocketConfig.Builder socketConfigBuilder = SocketConfig.custom();
            // Configure SOCKS proxy if enabled
            if (proxyManager.isProxyEnabled()) {
                SocksProxyConfiguration.SocksProxy selectedProxy = proxyManager.selectProxy();
                InetSocketAddress socksProxyAddress = new InetSocketAddress(selectedProxy.getHost(), selectedProxy.getPort());
                socketConfigBuilder.setSocksProxyAddress(socksProxyAddress);
            }
            socketConfigBuilder
                    .setSoTimeout(Timeout.ofSeconds(10))
                    .setSoLinger(TimeValue.ofSeconds(-1));

            return socketConfigBuilder.build();
        });

        connectionManager = connectionManagerBuilder.build();

        Thread.ofPlatform().daemon(true).start(() -> {
            try {
                for (;;) {
                    TimeUnit.SECONDS.sleep(15);
                    logger.info("Connection pool stats: {}", connectionManager.getTotalStats());
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        final RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setCookieSpec(StandardCookieSpec.IGNORE)
                .setResponseTimeout(10, TimeUnit.SECONDS)
                .setConnectionRequestTimeout(5, TimeUnit.MINUTES)
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setRetryStrategy(new RetryStrategy())
                .setKeepAliveStrategy(new ConnectionKeepAliveStrategy() {
                    // Default keep-alive duration is 3 minutes, but this is too long for us,
                    // as we are either going to re-use it fairly quickly or close it for a long time.
                    //
                    // So we set it to 30 seconds or clamp the server-provided value to a minimum of 10 seconds.
                    private static final TimeValue defaultValue = TimeValue.ofSeconds(30);

                    @Override
                    public TimeValue getKeepAliveDuration(HttpResponse response, HttpContext context) {
                        final Iterator<HeaderElement> it = MessageSupport.iterate(response, HeaderElements.KEEP_ALIVE);

                        while (it.hasNext()) {
                            final HeaderElement he = it.next();
                            final String param = he.getName();
                            final String value = he.getValue();

                            if (value == null)
                                continue;
                            if (!"timeout".equalsIgnoreCase(param))
                                continue;

                            try {
                                long timeout = Long.parseLong(value);
                                timeout = Math.clamp(timeout, 30, defaultValue.toSeconds());
                                return TimeValue.ofSeconds(timeout);
                            } catch (final NumberFormatException ignore) {
                                break;
                            }
                        }
                        return defaultValue;
                    }
                })
                .disableRedirectHandling()
                .setDefaultRequestConfig(defaultRequestConfig)
                .build();
    }

    @Override
    public HttpClient get() {
        return client;
    }
}

