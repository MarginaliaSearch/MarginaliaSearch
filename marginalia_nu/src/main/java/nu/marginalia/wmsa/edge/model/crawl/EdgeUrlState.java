package nu.marginalia.wmsa.edge.model.crawl;

/** This should correspond to EC_URL.STATE */
public enum EdgeUrlState {
    OK,
    REDIRECT,
    DEAD,
    ARCHIVED,
    DISQUALIFIED
}
