package nu.marginalia.api.searchquery.model.results.debug;

import java.util.List;

public record ResultRankingDetails(List<DebugFactorGroup> docFactorGroups,
                                   List<DebugTermFactorGroup> termFactorGroups)
{

}

