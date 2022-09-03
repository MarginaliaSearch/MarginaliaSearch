package nu.marginalia.wmsa.edge.model.search;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;

public record EdgeSearchResultKeywordScore(int set,
                                           String keyword,
                                           IndexBlock index,
                                           boolean title,
                                           boolean link,
                                           boolean site,
                                           boolean subject,
                                           boolean name) {
}
