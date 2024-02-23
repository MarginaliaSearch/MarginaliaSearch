package nu.marginalia.api.domains;

import lombok.SneakyThrows;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.api.domains.model.*;

import java.util.ArrayList;
import java.util.List;

public class DomainsProtobufCodec {

    public static class DomainQueries {
        public static RpcDomainLinksRequest createRequest(int domainId, int count) {
            return RpcDomainLinksRequest.newBuilder()
                    .setDomainId(domainId)
                    .setCount(count)
                    .build();
        }

        public static List<SimilarDomain> convertResponse(RpcSimilarDomains rsp) {
            List<SimilarDomain> ret = new ArrayList<>(rsp.getDomainsCount());

            for (RpcSimilarDomain sd : rsp.getDomainsList()) {
                ret.add(convertResponseEntry(sd));
            }

            return ret;
        }

        @SneakyThrows
        private static SimilarDomain convertResponseEntry(RpcSimilarDomain sd) {
            return new SimilarDomain(
                    new EdgeUrl(sd.getUrl()),
                    sd.getDomainId(),
                    sd.getRelatedness(),
                    sd.getRank(),
                    sd.getIndexed(),
                    sd.getActive(),
                    sd.getScreenshot(),
                    SimilarDomain.LinkType.valueOf(sd.getLinkType().name())
            );
        }
    }

    public static class DomainInfo {
        public static RpcDomainId createRequest(int domainId) {
            return RpcDomainId.newBuilder()
                    .setDomainId(domainId)
                    .build();
        }

        public static DomainInformation convertResponse(RpcDomainInfoResponse rsp) {
            return new DomainInformation(
                    new EdgeDomain(rsp.getDomain()),
                    rsp.getBlacklisted(),
                    rsp.getPagesKnown(),
                    rsp.getPagesFetched(),
                    rsp.getPagesIndexed(),
                    rsp.getIncomingLinks(),
                    rsp.getOutboundLinks(),
                    rsp.getNodeAffinity(),
                    rsp.getRanking(),
                    rsp.getSuggestForCrawling(),
                    rsp.getInCrawlQueue(),
                    rsp.getUnknownDomain(),
                    rsp.getIp(),
                    rsp.getAsn(),
                    rsp.getAsnOrg(),
                    rsp.getAsnCountry(),
                    rsp.getIpCountry(),
                    rsp.getState()
            );
        }
    }
}
