package nu.marginalia.wmsa.edge.search.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;

import java.util.List;

@Getter @AllArgsConstructor @ToString
public class DomainInformation {
    EdgeDomain domain;

    boolean blacklisted;
    int pagesKnown;
    int pagesFetched;
    int pagesIndexed;
    int incomingLinks;
    int outboundLinks;
    double ranking;

    EdgeDomainIndexingState state;
    List<EdgeDomain> linkingDomains;
}
