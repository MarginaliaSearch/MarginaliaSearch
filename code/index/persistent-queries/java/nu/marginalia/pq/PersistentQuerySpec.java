package nu.marginalia.pq;
import java.time.Instant;
import java.util.List;

// Maps to PERSISTENT_QUERIES in mariadb
public record PersistentQuerySpec(
        long internalId,
        String publicId,
        String customerId,
        List<String> termsInclude,
        List<String> termsExclude,
        String languageIsoCode,
        Instant tsAdded,
        Instant tsDisabled
) {

}
