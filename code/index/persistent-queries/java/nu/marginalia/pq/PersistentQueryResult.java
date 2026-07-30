package nu.marginalia.pq;

import java.time.Instant;

public record PersistentQueryResult(int id, String url, Instant addedTs) {

}
