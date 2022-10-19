package nu.marginalia.wmsa.edge.index.svc.query;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.svc.EdgeIndexQueryService;

import java.util.List;

public record IndexQueryParams(IndexBlock block,
                               EdgeIndexQueryService.SearchTerms searchTerms,
                               Integer qualityLimit,
                               Integer rankLimit,
                               List<Integer> targetDomains
                               )
{

}
