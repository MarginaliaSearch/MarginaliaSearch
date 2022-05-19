package nu.marginalia.wmsa.edge.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** This exists entirely for strengthening the typing of IDs
 *
 * @param <T>
 */
@AllArgsConstructor @Getter @EqualsAndHashCode @ToString
public class EdgeId<T> {
    private final int id;
}
