package nu.marginalia.wmsa.edge.model.search;

import nu.marginalia.wmsa.edge.index.model.IndexBlock;

public record EdgeSearchResultKeywordScore(int set,
                                           String keyword,
                                           IndexBlock index,
                                           boolean title,
                                           boolean link,
                                           boolean site,
                                           boolean subject,
                                           boolean name,
                                           boolean high,
                                           boolean mid,
                                           boolean low) {
    public double value() {
        double sum = 0;
        if (title)
            sum -= 15;
        if (link)
            sum -= 10;
        if (site)
            sum -= 10;
        if (subject)
            sum -= 10;
        if (high)
            sum -= 5;
        if (mid)
            sum -= 3;
        if (low)
            sum -= 2;
        if (name)
            sum -= -1;
        return sum;
    }
}
