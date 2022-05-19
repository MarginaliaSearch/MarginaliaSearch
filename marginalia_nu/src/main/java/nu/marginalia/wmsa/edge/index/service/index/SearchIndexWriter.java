package nu.marginalia.wmsa.edge.index.service.index;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeId;
import nu.marginalia.wmsa.edge.model.EdgeUrl;

import java.util.List;

public interface SearchIndexWriter {
    void put(EdgeId<EdgeDomain> domainId, EdgeId<EdgeUrl> urlId, IndexBlock block, List<String> words);
    void forceWrite();

    void flushWords();

}
