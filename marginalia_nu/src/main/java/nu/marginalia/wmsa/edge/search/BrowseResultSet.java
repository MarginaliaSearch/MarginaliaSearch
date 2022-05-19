package nu.marginalia.wmsa.edge.search;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class BrowseResultSet {
    public final List<BrowseResult> results;
}
