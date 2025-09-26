package nu.marginalia.proxy;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Configuration for SOCKS proxy settings used by crawlers to distribute IP footprint.
 */
public class SocksProxyConfiguration {
    
    private final boolean enabled;
    private final List<SocksProxy> proxies;
    private final ProxySelectionStrategy strategy;
    
    public SocksProxyConfiguration() {
        this.enabled = Boolean.parseBoolean(System.getProperty("crawler.socksProxy.enabled", "false"));
        this.strategy = ProxySelectionStrategy.valueOf(
            System.getProperty("crawler.socksProxy.strategy", "ROUND_ROBIN")
        );
        this.proxies = parseProxies();
    }
    
    private List<SocksProxy> parseProxies() {
        String proxyList = System.getProperty("crawler.socksProxy.list", "");
        if (proxyList.isEmpty()) {
            return List.of();
        }
        
        return Arrays.stream(proxyList.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::parseProxy)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
    
    private SocksProxy parseProxy(String proxyString) {
        try {
            // Expected format: "host:port" or "host:port:username:password"
            String[] parts = proxyString.split(":");
            if (parts.length < 2) {
                return null;
            }
            
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            
            if (parts.length >= 4) {
                String username = parts[2];
                String password = parts[3];
                return new SocksProxy(host, port, username, password);
            } else {
                return new SocksProxy(host, port);
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    public boolean isEnabled() {
        return enabled && !proxies.isEmpty();
    }
    
    public List<SocksProxy> getProxies() {
        return proxies;
    }
    
    public ProxySelectionStrategy getStrategy() {
        return strategy;
    }
    
    public enum ProxySelectionStrategy {
        ROUND_ROBIN,
        RANDOM
    }
    
    public static class SocksProxy {
        private final String host;
        private final int port;
        private final String username;
        private final String password;
        
        public SocksProxy(String host, int port) {
            this(host, port, null, null);
        }
        
        public SocksProxy(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }
        
        public String getHost() {
            return host;
        }
        
        public int getPort() {
            return port;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPassword() {
            return password;
        }
        
        public boolean hasAuthentication() {
            return username != null && password != null;
        }
        
        @Override
        public String toString() {
            if (hasAuthentication()) {
                return String.format("%s:%d (auth: %s)", host, port, username);
            } else {
                return String.format("%s:%d", host, port);
            }
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SocksProxy that = (SocksProxy) o;
            return port == that.port && 
                   Objects.equals(host, that.host) && 
                   Objects.equals(username, that.username) && 
                   Objects.equals(password, that.password);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(host, port, username, password);
        }
    }
}
