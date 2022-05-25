package nu.marginalia.wmsa.edge.crawling.blocklist;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import org.apache.commons.net.util.SubnetUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Singleton
public class IpBlockList {
    private final GeoIpBlocklist geoIpBlocklist;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final List<SubnetUtils.SubnetInfo> badSubnets = new ArrayList<>();

    @Inject
    public IpBlockList(GeoIpBlocklist geoIpBlocklist) {
        this.geoIpBlocklist = geoIpBlocklist;

        var resource = Objects.requireNonNull(
                ClassLoader.getSystemResourceAsStream("ip-banned-cidr.txt"),
                "Could not load IP blacklist");

        try (var reader = new BufferedReader(new InputStreamReader(resource, StandardCharsets.UTF_8))) {

            for (;;) {
                var cidr = reader.readLine();
                if (cidr == null) {
                    break;
                }
                if (!cidr.isBlank() && !cidr.startsWith("#") && !cidr.contains(":")) {
                    badSubnets.add(new SubnetUtils(cidr).getInfo());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to read IP list");
        }

        logger.info("Loaded {} CIDRs", badSubnets.size());
    }

    final Predicate<String> numericPattern = Pattern.compile(".*\\d{4}.*").asMatchPredicate();

    public boolean isAllowed(EdgeDomain domain) {
        if (domain.domain.endsWith(".cn")) {
            logger.debug("Blocking {} on .cn-end", domain);
            return false;
        }
        if (numericPattern.test(domain.toString())) {
            logger.debug("Blocking {} on numeric", domain);
            return false;
        }

        try {
            var hostAddress = InetAddressCache.getAddress(domain).getHostAddress();
            var subnet = badSubnets.stream().filter(sn -> sn.isInRange(hostAddress)).findFirst();
            if (subnet.isPresent()) {
                logger.debug("Blocking {} on IP range: {}", domain, subnet.get());
                return false;
            }
        } catch (Throwable t) {
            return false;
        }

        var geo = geoIpBlocklist.isAllowed(domain);
        if (!geo) {
            logger.debug("Blocking {} on geo blocklist", domain);
        }
        return geo;
    }

}
