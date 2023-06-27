package nu.marginalia.search.model;

import lombok.*;
import nu.marginalia.model.EdgeDomain;

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
    boolean unknownDomain;

    String state;
    List<EdgeDomain> linkingDomains;
}
