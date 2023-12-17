package nu.marginalia.geoip.sources;

import com.opencsv.CSVReader;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/** Load an IP2LOCATION LITE database file and provide a method to look up the country for an IP address.
 */
public class IP2LocationMapping {
    private final IpRangeMapping<String> ranges = new IpRangeMapping<>();

    public IP2LocationMapping(Path filename) {
        try (var reader = new CSVReader(Files.newBufferedReader(filename))) {
            for (;;) {
                String[] vals = reader.readNext();
                if (vals == null) {
                    break;
                }

                ranges.add(Integer.parseUnsignedInt(vals[0]), Integer.parseUnsignedInt(vals[1]), vals[2]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getCountry(String ip) {
        try {
            return getCountry(InetAddress.getByName(ip));
        } catch (Exception e) {
            return "";
        }
    }

    public String getCountry(InetAddress address) {
        return ranges.get(address).orElse("");
    }
}
