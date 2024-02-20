package nu.marginalia.assistant.client;

import lombok.SneakyThrows;
import nu.marginalia.assistant.api.*;
import nu.marginalia.assistant.client.model.DictionaryEntry;
import nu.marginalia.assistant.client.model.DictionaryResponse;
import nu.marginalia.assistant.client.model.DomainInformation;
import nu.marginalia.assistant.client.model.SimilarDomain;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

import java.util.ArrayList;
import java.util.List;

public class AssistantProtobufCodec {

    public static class DictionaryLookup {
        public static RpcDictionaryLookupRequest createRequest(String word) {
            return RpcDictionaryLookupRequest.newBuilder()
                    .setWord(word)
                    .build();
        }
        public static DictionaryResponse convertResponse(RpcDictionaryLookupResponse rsp) {
            return new DictionaryResponse(
                    rsp.getWord(),
                    rsp.getEntriesList().stream().map(DictionaryLookup::convertResponseEntry).toList()
            );
        }

        private static DictionaryEntry convertResponseEntry(RpcDictionaryEntry e) {
            return new DictionaryEntry(e.getType(), e.getWord(), e.getDefinition());
        }
    }

    public static class SpellCheck {
        public static RpcSpellCheckRequest createRequest(String text) {
            return RpcSpellCheckRequest.newBuilder()
                    .setText(text)
                    .build();
        }

        public static List<String> convertResponse(RpcSpellCheckResponse rsp) {
            return rsp.getSuggestionsList();
        }
    }

    public static class UnitConversion {
        public static RpcUnitConversionRequest createRequest(String from, String to, String unit) {
            return RpcUnitConversionRequest.newBuilder()
                    .setFrom(from)
                    .setTo(to)
                    .setUnit(unit)
                    .build();
        }

        public static String convertResponse(RpcUnitConversionResponse rsp) {
            return rsp.getResult();
        }
    }

    public static class EvalMath {
        public static RpcEvalMathRequest createRequest(String expression) {
            return RpcEvalMathRequest.newBuilder()
                    .setExpression(expression)
                    .build();
        }

        public static String convertResponse(RpcEvalMathResponse rsp) {
            return rsp.getResult();
        }
    }

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
