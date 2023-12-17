package nu.marginalia.geoip.sources;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class AsnMapping {
    private static final Logger logger = LoggerFactory.getLogger(AsnMapping.class);
    private final IpRangeMapping<Integer> ranges = new IpRangeMapping<>();

    public AsnMapping(Path databaseFile) {
        try (var reader = Files.lines(databaseFile)) {
            reader.forEach(this::parseAsnMappingFileLine);
        } catch (Exception e) {
            logger.error("Failed to load ASN mapping" + databaseFile, e);
        }
    }

    public Optional<Integer> getAsnNumber(int ip) {
        return ranges.get(ip);
    }

    private void parseAsnMappingFileLine(String s) {
        try {
            String[] parts = StringUtils.split(s, '\t');
            if (parts.length != 2) {
                return;
            }

            // Parse CIDR notation, e.g. 127.0.0.1/24 -> ["127.0.0.1", "24"]
            String[] cidrParts = StringUtils.split(parts[0], '/');
            if (cidrParts.length != 2) {
                return;
            }

            // Parse IP address and subnet mask
            String[] ipParts = StringUtils.split(cidrParts[0], '.');
            int ipMask = Integer.parseInt(cidrParts[1]);

            // Convert subnet mask to integer start and end values
            int ipStart = 0;
            int ipEnd = 0;
            for (int i = 0; i < 4; i++) {
                int ipByte = Integer.parseInt(ipParts[i]);
                ipStart |= ipByte << (24 - 8 * i);
                ipEnd |= ipByte << (24 - 8 * i);
            }
            ipStart &= 0xFFFFFFFF << (32 - ipMask);
            ipEnd |= 0xFFFFFFFF >>> ipMask;


            ranges.add(ipStart, ipEnd, Integer.parseInt(parts[1]));

        }
        catch (Exception ex) {
            logger.warn("Failed to parse ASN mapping line: {}", s);
        }
    }

}
