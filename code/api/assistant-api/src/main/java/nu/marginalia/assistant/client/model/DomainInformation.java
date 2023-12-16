package nu.marginalia.assistant.client.model;

import lombok.*;
import nu.marginalia.model.EdgeDomain;

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
    int nodeAffinity;
    double ranking;

    boolean suggestForCrawling;
    boolean inCrawlQueue;
    boolean unknownDomain;

    String ip;
    Integer asn;
    String asnOrg;
    String ipCountry;
    String state;

    public String getIpFlag() {
        if (ipCountry == null || ipCountry.codePointCount(0, ipCountry.length()) != 2) {
            return "";
        }
        String country = ipCountry;

        if ("UK".equals(country)) {
            country = "GB";
        }

        int offset = 0x1F1E6;
        int asciiOffset = 0x41;
        int firstChar = Character.codePointAt(country, 0) - asciiOffset + offset;
        int secondChar = Character.codePointAt(country, 1) - asciiOffset + offset;
        return new String(Character.toChars(firstChar)) + new String(Character.toChars(secondChar));
    }
}
