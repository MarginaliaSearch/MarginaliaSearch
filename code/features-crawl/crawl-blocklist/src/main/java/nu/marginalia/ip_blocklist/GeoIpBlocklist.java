package nu.marginalia.ip_blocklist;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.geoip.GeoIpDictionary;
import nu.marginalia.model.EdgeDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

@Singleton
public class GeoIpBlocklist {
    /** These countries are extremely overrepresented among the problematic and spammy domains,
     *  and blocking them is by far the most effective spam mitigation technique.  Sucks we throw
     *  babies out with the bathwater, but it's undeniably effective.
     */
    private final Set<String> blacklist = Set.of("CN", "HK");
    private final Set<String> graylist = Set.of("RU", "TW", "IN", "ZA", "SG", "UA");

    private static final Logger logger = LoggerFactory.getLogger(GeoIpBlocklist.class);

    private final GeoIpDictionary ipDictionary;

    @Inject
    public GeoIpBlocklist(GeoIpDictionary ipDictionary) {
        this.ipDictionary = ipDictionary;
        ipDictionary.waitReady();
    }

    public boolean isAllowed(EdgeDomain domain) {
        String country = getCountry(domain);

        if (blacklist.contains(country)) {
            return false;
        }
        if (graylist.contains(country)) {
            return "www".equals(domain.subDomain);
        }

        return true;
    }

    public String getCountry(EdgeDomain domain) {
        try {
            return ipDictionary.getCountry(InetAddressCache.getAddress(domain));
        }
        catch (Throwable ex) {
            logger.debug("Failed to resolve {}", domain);
            return "-";
        }
    }
}
