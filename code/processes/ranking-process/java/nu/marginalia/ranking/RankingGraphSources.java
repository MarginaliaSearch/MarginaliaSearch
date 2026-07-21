package nu.marginalia.ranking;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.domaingraph.GraphSource;
import nu.marginalia.domaingraph.LinkGraphSource;
import nu.marginalia.domaingraph.SimilarityGraphSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Selects the domain graph to base a ranking calculation on.  The similarity graph
 * is preferred, but is not present in all environments. */
@Singleton
public class RankingGraphSources {
    private static final Logger logger = LoggerFactory.getLogger(RankingGraphSources.class);

    private final GraphSource similarityDomains;
    private final GraphSource linksDomains;

    @Inject
    public RankingGraphSources(LinkGraphSource rankingDomains,
                               SimilarityGraphSource similarityDomains) {
        if (similarityDomains.isAvailable()) {
            this.similarityDomains = similarityDomains;
            this.linksDomains = rankingDomains;
        }
        else {
            logger.info("Domain similarity is not present, falling back on link graph");
            this.similarityDomains = rankingDomains;
            this.linksDomains = rankingDomains;
        }
    }

    /** The similarity ranking does not behave well with an empty list of origin domains,
     * so the link graph is used in that case. */
    public GraphSource forDomainList(List<String> domains) {
        if (domains.isEmpty())
            return linksDomains;
        else
            return similarityDomains;
    }
}
