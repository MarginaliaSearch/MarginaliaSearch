package nu.marginalia.geoip;

import nu.marginalia.WmsaHome;
import nu.marginalia.geoip.sources.AsnMapping;
import nu.marginalia.geoip.sources.AsnTable;
import nu.marginalia.geoip.sources.IP2LocationMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.Optional;

public class GeoIpDictionary {
    private volatile IP2LocationMapping ip2locMapping = null;
    private volatile AsnTable asnTable = null;
    private volatile AsnMapping asnMapping = null;
    private static final Logger logger = LoggerFactory.getLogger(GeoIpDictionary.class);

    volatile boolean ready = false;

    public GeoIpDictionary() {
        Thread.ofPlatform().start(() -> {
            this.asnTable = new AsnTable(WmsaHome.getAsnInfoDatabase());
            logger.info("Loaded ASN table");
            this.asnMapping = new AsnMapping(WmsaHome.getAsnMappingDatabase());
            logger.info("Loaded ASN mapping");
            this.ip2locMapping = new IP2LocationMapping(WmsaHome.getIPLocationDatabse());

            ready = true;

            synchronized (this) {
                this.notifyAll();
            }
        });
    }

    public boolean isReady() {
        return ready;
    }

    public boolean waitReady() {
        while (!ready) {
            try {
                synchronized (this) {
                    this.wait(1000);
                }
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }


    public String getCountry(String ip) {
        if (null == ip2locMapping) {
            return "";
        }
        return ip2locMapping.getCountry(ip);
    }

    public String getCountry(InetAddress address) {
        if (null == ip2locMapping) {
            return "";
        }

        return ip2locMapping.getCountry(address);
    }

    public Optional<AsnTable.AsnInfo> getAsnInfo(String ip) {
        try {
            return getAsnInfo(InetAddress.getByName(ip));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<AsnTable.AsnInfo> getAsnInfo(int ipAddress) {
        if (null == asnTable) { // not loaded yet or failed to load
            return Optional.empty();
        }

        return asnMapping
                .getAsnNumber(ipAddress)
                .flatMap(asn -> asnTable.getAsnInfo(asn));
    }

    public Optional<AsnTable.AsnInfo> getAsnInfo(InetAddress address) {
        byte[] bytes = address.getAddress();

        int ival = (int) (((long)bytes[0]&0xFF) << 24 | ((long)bytes[1]&0xFF) << 16 | ((long)bytes[2]&0xFF)<< 8 | ((long)bytes[3]&0xFF));

        return getAsnInfo(ival);
    }
}
