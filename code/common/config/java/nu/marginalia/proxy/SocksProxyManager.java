package nu.marginalia.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages SOCKS proxy selection and rotation for crawler requests.
 */
public class SocksProxyManager {
    private static final Logger logger = LoggerFactory.getLogger(SocksProxyManager.class);
    
    private final SocksProxyConfiguration config;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    
    public SocksProxyManager(SocksProxyConfiguration config) {
        this.config = config;
        
        if (config.isEnabled()) {
            logger.info("SOCKS proxy support enabled with {} proxies using {} strategy", 
                       config.getProxies().size(), config.getStrategy());
            for (SocksProxyConfiguration.SocksProxy proxy : config.getProxies()) {
                logger.info("  - {}", proxy);
            }
        } else {
            logger.info("SOCKS proxy support disabled");
        }
    }
    
    /**
     * Selects the next proxy to use based on the configured strategy.
     * Returns null if proxy support is disabled or no proxies are available.
     */
    public SocksProxyConfiguration.SocksProxy selectProxy() {
        if (!config.isEnabled()) {
            return null;
        }
        
        List<SocksProxyConfiguration.SocksProxy> proxies = config.getProxies();
        if (proxies.isEmpty()) {
            return null;
        }
        
        SocksProxyConfiguration.SocksProxy selectedProxy;
        switch (config.getStrategy()) {
            case ROUND_ROBIN:
                int index = roundRobinIndex.getAndIncrement() % proxies.size();
                selectedProxy = proxies.get(index);
                break;
            case RANDOM:
                int randomIndex = ThreadLocalRandom.current().nextInt(proxies.size());
                selectedProxy = proxies.get(randomIndex);
                break;
            default:
                selectedProxy = proxies.get(0);
                break;
        }
        
        logger.debug("Selected SOCKS proxy: {}", selectedProxy);
        return selectedProxy;
    }
    
    /**
     * Gets the current proxy configuration.
     */
    public SocksProxyConfiguration getConfiguration() {
        return config;
    }
    
    /**
     * Checks if proxy support is enabled and proxies are available.
     */
    public boolean isProxyEnabled() {
        return config.isEnabled();
    }
}
