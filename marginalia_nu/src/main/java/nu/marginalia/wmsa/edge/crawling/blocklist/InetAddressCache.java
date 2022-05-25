package nu.marginalia.wmsa.edge.crawling.blocklist;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class InetAddressCache {
    private static final Cache<EdgeDomain, InetAddress> cache = CacheBuilder.newBuilder().maximumSize(10_000_000).expireAfterAccess(1, TimeUnit.HOURS).build();
    public static InetAddress getAddress(EdgeDomain domain) throws Throwable {
        try {
            return cache.get(domain, ()->{
                return InetAddress.getByName(domain.getAddress());
            });
        }
        catch (ExecutionException ex) {
            throw ex.getCause();
        }
    }
}
