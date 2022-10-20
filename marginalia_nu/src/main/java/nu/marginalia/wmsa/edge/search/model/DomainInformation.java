package nu.marginalia.wmsa.edge.search.model;

import lombok.*;
import nu.marginalia.wmsa.edge.model.EdgeDomain;

import java.util.List;

@Getter @AllArgsConstructor @NoArgsConstructor @Builder
@ToString
public class DomainInformation {
    EdgeDomain domain;

    boolean blacklisted;
    int pagesKnown;
    int pagesFetched;
    int pagesIndexed;
    int incomingLinks;
    int outboundLinks;
    double ranking;

    boolean suggestForCrawling;
    boolean inCrawlQueue;

    String state;
    List<EdgeDomain> linkingDomains;
}
