package nu.marginalia.wmsa.edge.model.search;

import lombok.*;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.SearchOrder;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ToString @Getter @Builder @With @AllArgsConstructor
public class EdgeSearchSpecification {

    public List<Integer> buckets;
    public List<EdgeSearchSubquery> subqueries;
    public final int limitByBucket;
    public final int limitByDomain;
    public final int limitTotal;

    public final String humanQuery;
    public final SearchOrder searchOrder;
    public boolean stagger;
    public boolean experimental;

    public static EdgeSearchSpecification justIncludes(String... words) {
        return new EdgeSearchSpecification(Collections.emptyList(), Collections.singletonList(new EdgeSearchSubquery(Arrays.asList(words), Collections.emptyList(), IndexBlock.Title)), 10, 10, 10, "", SearchOrder.ASCENDING, false, false);
    }

}
