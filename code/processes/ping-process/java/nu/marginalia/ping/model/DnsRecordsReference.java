package nu.marginalia.ping.model;

import com.google.gson.Gson;
import nu.marginalia.model.gson.GsonFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.zip.CRC32;

/** Opaque reference to a list-style DNS record.
 *
 * These can be considerably long, so we may need to store their hash instead of the actual value
 * in order to be able to perform a comparison, this object holds either representation.
 */
public sealed interface DnsRecordsReference {

    static Gson gson = GsonFactory.get();

    static final String EMPTY_HASH = "#";
    static int MAX_FIELD_LENGTH = 255;

    public static DnsRecordsReference ofValues(String... values) {
        return new ListValue(Set.of(values));
    }

    /** Decode the encoded representation,
     * which is either a hash code represented by the prefix #,
     * or a JSON-style list of strings.
     * */
    static DnsRecordsReference decode(String encodedValue) {
        if (encodedValue == null || encodedValue.isEmpty()) {
            return new ListValue(Set.of());
        }

        if (encodedValue.startsWith("#"))
            return new HashValue(encodedValue);

        List<String> items = gson.fromJson(encodedValue, List.class);

        // blow up sooner rather than later if assertions are enabled
        // this is supposed to be the output of encode() so not really a risk in prod
        if (DnsRecordsReference.class.desiredAssertionStatus()
            && !items.stream().allMatch(String.class::isInstance)) {
            throw new IllegalStateException("Error in deserializing DNS fields, produced something not compatible with List<String>");
        }

        return new ListValue(new HashSet<>(items));
    }

    record ListValue(Set<String> values) implements DnsRecordsReference {

        public ListValue(Set<String> values) {
            if (values == null) {
                this.values = Set.of();
            }
            else {
                this.values = values;
            }
        }

        public String asHash() {
            if (values.isEmpty())
                return EMPTY_HASH;

            List<String> valueList = new ArrayList<>(values);
            valueList.sort(Comparator.naturalOrder());

            long hash64 = 0;
            for (String value : valueList) {
                hash64 = hash64 * 31 + value.hashCode();
            }

            return "#" + Long.toHexString(hash64);
        }

        public String encode() {
            // Since the order of DNS records are not semantically meaningful,
            // and can change between requests, we'll store them in a sorted order
            // to simplify eyeball comparison, but store them as a set in memory

            String json = gson.toJson(values.stream().sorted().toList());
            if (json.length() >= MAX_FIELD_LENGTH) {
                return asHash();
            } else {
                return json;
            }
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof DnsRecordsReference ref) {
                return isEquivalent(this, ref);
            }
            return false;
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        @Override
        public int hashCode() {
            // ensure hashCode compatibility with HashValue representation
            return asHash().hashCode();
        }
    }

    record HashValue(String hash) implements DnsRecordsReference {
        public String asHash() {
            return hash;
        }

        public String encode() {
            return asHash();
        }


        @Override
        public boolean equals(Object other) {
            if (other instanceof DnsRecordsReference ref) {
                return isEquivalent(this, ref);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return asHash().hashCode();
        }

        public boolean isEmpty() {
            return EMPTY_HASH.equals(hash);
        }
    }

    public String asHash();
    public String encode();
    public boolean isEmpty();

    public static boolean isEquivalent(
            @Nullable DnsRecordsReference a,
            @Nullable DnsRecordsReference b) {
        if (a == null || b == null)
            return a == b;

        if (a instanceof ListValue lva && b instanceof ListValue lvb) {
            return Objects.equals(lva.values(), lvb.values());
        }

        return Objects.equals(a.asHash(), b.asHash());
    }
}
