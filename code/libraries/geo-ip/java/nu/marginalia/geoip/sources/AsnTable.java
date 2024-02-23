package nu.marginalia.geoip.sources;

import nu.marginalia.WmsaHome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

public class AsnTable {
    public HashMap<Integer, AsnInfo> asns = new HashMap<>(65536);
    public record AsnInfo(int asn, String country, String org) {}
    private static final Logger logger = LoggerFactory.getLogger(AsnTable.class);

    public AsnTable(Path asnFile) {
        try (var reader = Files.lines(WmsaHome.getAsnInfoDatabase())) {
            reader.map(AsnTable::parseAsnFileLine).filter(Objects::nonNull).forEach(asn -> asns.put(asn.asn(), asn));
        } catch (Exception e) {
            logger.error("Failed to load ASN database " + asnFile, e);
        }
    }

    public Optional<AsnInfo> getAsnInfo(int asn) {
        return Optional.ofNullable(asns.get(asn));
    }

    static AsnInfo parseAsnFileLine(String line) {
        line = line.trim();

        try {
            int numEnd = line.indexOf(' ');
            String num = line.substring(0, numEnd);

            int asn = Integer.parseInt(num);

            int orgStart = numEnd + 1;
            int orgEnd = line.lastIndexOf(',');
            if (orgEnd < 0 || orgEnd < orgStart + 1) {
                orgEnd = line.length();
            }

            String org = line.substring(orgStart, orgEnd);
            String country = "";
            if (orgEnd + 1 < line.length()) {
                country = line.substring(orgEnd + 1).trim();
            }

            if ("UNALLOCATED".equals(org)) {
                return  null;
            }

            return new AsnInfo(asn, country, org);
        }
        catch (Exception ex) {
            logger.warn("Failed to parse ASN line: {}", line);
            return null;
        }
    }
}
