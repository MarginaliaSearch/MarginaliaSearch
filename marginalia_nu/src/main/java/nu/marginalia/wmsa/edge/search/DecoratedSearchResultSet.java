package nu.marginalia.wmsa.edge.search;

import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.search.EdgeUrlDetails;

import java.util.List;
import java.util.Objects;

@ToString @Getter
public class DecoratedSearchResultSet {
    public final List<EdgeUrlDetails> resultSet;

    public int size() {
        return resultSet.size();
    }

    public DecoratedSearchResultSet(List<EdgeUrlDetails> resultSet) {
        this.resultSet = Objects.requireNonNull(resultSet);
    }

}
