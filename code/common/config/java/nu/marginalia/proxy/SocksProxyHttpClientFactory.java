package nu.marginalia.proxy;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.protocol.HttpContext;
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
    public static ConnectionSocketFactory createSocksSocketFactory(SocksProxy proxy) {
        return new ConnectionSocketFactory() {
            @Override
            public Socket createSocket(HttpContext context) throws IOException {
                Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, 
                    new InetSocketAddress(proxy.getHost(), proxy.getPort())));
                
                // Set socket timeouts
                socket.setSoTimeout(30000); // 30 seconds
                socket.setConnectTimeout(30000); // 30 seconds
                
                return socket;
            }
            
            @Override
            public Socket connectSocket(int connectTimeout, Socket socket, HttpContext context, 
                                      InetSocketAddress remoteAddress, InetSocketAddress localAddress, 
                                      org.apache.hc.core5.util.Timeout socketTimeout) throws IOException {
                if (socket == null) {
                    socket = createSocket(context);
                }
                
                if (localAddress != null) {
                    socket.bind(localAddress);
                }
                
                socket.connect(remoteAddress, connectTimeout);
                return socket;
            }
        };
    }
    
    /**
     * Creates a SOCKS proxy SSL socket factory for the given proxy configuration.
     */
    public static SSLConnectionSocketFactory createSocksSslSocketFactory(SocksProxy proxy) {
        try {
            return new SSLConnectionSocketFactory(SSLContext.getDefault()) {
                @Override
                public Socket createSocket(HttpContext context) throws IOException {
                    Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, 
                        new InetSocketAddress(proxy.getHost(), proxy.getPort())));
                    
                    // Set socket timeouts
                    socket.setSoTimeout(30000); // 30 seconds
                    socket.setConnectTimeout(30000); // 30 seconds
                    
                    return socket;
                }
                
                @Override
                public Socket connectSocket(int connectTimeout, Socket socket, HttpContext context, 
                                          InetSocketAddress remoteAddress, InetSocketAddress localAddress, 
                                          org.apache.hc.core5.util.Timeout socketTimeout) throws IOException {
                    if (socket == null) {
                        socket = createSocket(context);
                    }
                    
                    if (localAddress != null) {
                        socket.bind(localAddress);
                    }
                    
                    socket.connect(remoteAddress, connectTimeout);
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
                                                 SocksProxy proxy) {
        if (proxy != null) {
            logger.debug("Configuring HTTP client with SOCKS proxy: {}", proxy);
            
            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", createSocksSocketFactory(proxy))
                    .register("https", createSocksSslSocketFactory(proxy))
                    .build();
            
            builder.setConnectionManager(registry);
        } else {
            logger.debug("Configuring HTTP client without proxy");
        }
    }
}
