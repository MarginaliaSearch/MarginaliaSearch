package nu.marginalia.geoip.sources;

import java.net.InetAddress;
import java.util.Optional;
import java.util.TreeMap;

public class IpRangeMapping<T> {
    private final TreeMap<Integer, IpRangeWithCountry<T>> ranges = new TreeMap<>(Integer::compareUnsigned);

    public record IpRangeWithCountry<T>(int ipStart, int ipEnd, T value) {
        public boolean contains(int ip) {
            return Integer.compareUnsigned(ipStart, ip) <= 0
                    && Integer.compareUnsigned(ip, ipEnd) < 0;
        }
    }

    public void add(int ipStart, int ipEnd, T value) {
        ranges.put(ipStart, new IpRangeWithCountry<>(ipStart, ipEnd, value));
    }

    public Optional<T> get(InetAddress address) {
        byte[] bytes = address.getAddress();
        int ival = (int) (((long) bytes[0] & 0xFF) << 24 | ((long) bytes[1] & 0xFF) << 16 | ((long) bytes[2] & 0xFF) << 8 | ((long) bytes[3] & 0xFF));

        return get(ival);
    }

    public Optional<T> get(int ipUnsignedInt) {
        Integer key = ranges.floorKey(ipUnsignedInt);
        if (null == key) {
            return Optional.empty();
        }

        var range = ranges.get(key);
        if (range.contains(ipUnsignedInt)) {
            return Optional.of(range.value);
        }

        return Optional.empty();
    }
}
