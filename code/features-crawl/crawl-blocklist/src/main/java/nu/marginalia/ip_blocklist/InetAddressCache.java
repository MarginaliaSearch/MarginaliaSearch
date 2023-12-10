package nu.marginalia.ip_blocklist;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import nu.marginalia.model.EdgeDomain;

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

// We don't want to torture the DNS by resolving the same links over and over and over again

public class InetAddressCache {
    private static final Cache<EdgeDomain, InetAddress> cache = CacheBuilder.newBuilder().maximumSize(1_000_000).expireAfterAccess(1, TimeUnit.HOURS).build();
    public static InetAddress getAddress(EdgeDomain domain) throws Throwable {
        try {
            return cache.get(domain, ()-> InetAddress.getByName(domain.getAddress()));
        }
        catch (ExecutionException ex) {
            throw ex.getCause();
        }
    }
}
