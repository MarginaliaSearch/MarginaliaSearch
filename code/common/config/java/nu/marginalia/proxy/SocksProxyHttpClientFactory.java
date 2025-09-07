package nu.marginalia.proxy;

import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Utility class for configuring HTTP clients with SOCKS proxy support.
 */
public class SocksProxyHttpClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(SocksProxyHttpClientFactory.class);
    
    /**
     * Configures a connection manager builder with SOCKS proxy support.
     * If no proxy is provided, uses default socket configuration.
     */
    public static void configureConnectionManager(PoolingHttpClientConnectionManagerBuilder builder, 
                                                 SocksProxyConfiguration.SocksProxy proxy) {
        if (proxy != null) {
            logger.debug("Configuring HTTP client with SOCKS proxy: {}", proxy);
            
            // Create SOCKS proxy address
            InetSocketAddress socksProxyAddress = new InetSocketAddress(proxy.getHost(), proxy.getPort());
            
            // Configure socket config with SOCKS proxy
            SocketConfig socketConfig = SocketConfig.custom()
                    .setSocksProxyAddress(socksProxyAddress)
                    .setSoTimeout(Timeout.ofSeconds(30))
                    .build();
            
            // Apply the socket configuration to the connection manager
            builder.setDefaultSocketConfig(socketConfig);
            
            logger.info("SOCKS proxy configured: {}:{}", proxy.getHost(), proxy.getPort());
        } else {
            logger.debug("Configuring HTTP client without proxy");
        }
    }
}
