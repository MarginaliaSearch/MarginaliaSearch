package nu.marginalia.ranking.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import nu.marginalia.model.crawl.DomainIndexingState;

@Data
@AllArgsConstructor
public class RankingDomainData {
    public final int id;
    public final String name;
    private int alias;
    public DomainIndexingState state;
    public final int knownUrls;

    public int resolveAlias() {
        if (alias == 0) return id;
        return alias;
    }

    public boolean isAlias() {
        return alias != 0;
    }

    public boolean isSpecial() {
        return DomainIndexingState.SPECIAL == state;
    }

    public boolean isSocialMedia() {
        return DomainIndexingState.SOCIAL_MEDIA == state;
    }
}
