package nu.marginalia.api.searchquery.model.results.debug;

import java.util.List;

public record DebugTermFactorGroup(String term, long termId, List<DebugFactorGroup> factorList) {
}
