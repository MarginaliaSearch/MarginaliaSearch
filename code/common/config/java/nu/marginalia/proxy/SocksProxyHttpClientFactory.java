package nu.marginalia.proxy;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.pool.ConnPoolControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * Utility class for configuring HTTP clients with SOCKS proxy support.
 */
public class SocksProxyHttpClientFactory {
    private static final Logger logger = LoggerFactory.getLogger(SocksProxyHttpClientFactory.class);
    
    /**
     * Creates a SOCKS proxy socket factory for the given proxy configuration.
     */
    public static ConnectionSocketFactory createSocksSocketFactory(SocksProxyConfiguration.SocksProxy proxy) {
        return new ConnectionSocketFactory() {
            @Override
            public Socket createSocket(HttpContext context) throws IOException {
                Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, 
                    new InetSocketAddress(proxy.getHost(), proxy.getPort())));
                
                // Set socket timeouts
                socket.setSoTimeout(30000); // 30 seconds
                
                return socket;
            }
            
            @Override
            public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host, 
                                      InetSocketAddress remoteAddress, InetSocketAddress localAddress, 
                                      HttpContext context) throws IOException {
                if (socket == null) {
                    socket = createSocket(context);
                }
                
                if (localAddress != null) {
                    socket.bind(localAddress);
                }
                
                int timeoutMs = connectTimeout != null ? (int) connectTimeout.toMilliseconds() : 30000;
                socket.connect(remoteAddress, timeoutMs);
                return socket;
            }
        };
    }
    
    /**
     * Creates a SOCKS proxy SSL socket factory for the given proxy configuration.
     */
    public static SSLConnectionSocketFactory createSocksSslSocketFactory(SocksProxyConfiguration.SocksProxy proxy) {
        try {
            return new SSLConnectionSocketFactory(SSLContext.getDefault()) {
                @Override
                public Socket createSocket(HttpContext context) throws IOException {
                    Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, 
                        new InetSocketAddress(proxy.getHost(), proxy.getPort())));
                    
                    // Set socket timeouts
                    socket.setSoTimeout(30000); // 30 seconds
                    
                    return socket;
                }
                
                @Override
                public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host, 
                                          InetSocketAddress remoteAddress, InetSocketAddress localAddress, 
                                          HttpContext context) throws IOException {
                    if (socket == null) {
                        socket = createSocket(context);
                    }
                    
                    if (localAddress != null) {
                        socket.bind(localAddress);
                    }
                    
                    int timeoutMs = connectTimeout != null ? (int) connectTimeout.toMilliseconds() : 30000;
                    socket.connect(remoteAddress, timeoutMs);
                    return socket;
                }
            };
        } catch (Exception e) {
            logger.error("Failed to create SOCKS SSL socket factory", e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Configures a connection manager builder with SOCKS proxy support.
     * If no proxy is provided, uses default socket factories.
     */
    public static void configureConnectionManager(PoolingHttpClientConnectionManagerBuilder builder, 
                                                 SocksProxyConfiguration.SocksProxy proxy) {
        if (proxy != null) {
            logger.debug("Configuring HTTP client with SOCKS proxy: {}", proxy);
            
            // For HTTP Components v5, SOCKS proxy support needs to be configured differently
            // This is a placeholder implementation - the actual SOCKS proxy configuration
            // would need to be handled at the JVM level or through system properties
            logger.warn("SOCKS proxy configuration not fully implemented for HTTP Components v5");
        } else {
            logger.debug("Configuring HTTP client without proxy");
        }
    }
}
