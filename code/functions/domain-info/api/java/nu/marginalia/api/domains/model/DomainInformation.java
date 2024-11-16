package nu.marginalia.api.domains.model;

import nu.marginalia.model.EdgeDomain;

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
    String asnCountry;

    String ipCountry;
    String state;

    public DomainInformation(EdgeDomain domain, boolean blacklisted, int pagesKnown, int pagesFetched, int pagesIndexed, int incomingLinks, int outboundLinks, int nodeAffinity, double ranking, boolean suggestForCrawling, boolean inCrawlQueue, boolean unknownDomain, String ip, Integer asn, String asnOrg, String asnCountry, String ipCountry, String state) {
        this.domain = domain;
        this.blacklisted = blacklisted;
        this.pagesKnown = pagesKnown;
        this.pagesFetched = pagesFetched;
        this.pagesIndexed = pagesIndexed;
        this.incomingLinks = incomingLinks;
        this.outboundLinks = outboundLinks;
        this.nodeAffinity = nodeAffinity;
        this.ranking = ranking;
        this.suggestForCrawling = suggestForCrawling;
        this.inCrawlQueue = inCrawlQueue;
        this.unknownDomain = unknownDomain;
        this.ip = ip;
        this.asn = asn;
        this.asnOrg = asnOrg;
        this.asnCountry = asnCountry;
        this.ipCountry = ipCountry;
        this.state = state;
    }

    public DomainInformation() {
    }

    public static DomainInformationBuilder builder() {
        return new DomainInformationBuilder();
    }

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

    public EdgeDomain getDomain() {
        return this.domain;
    }

    public boolean isBlacklisted() {
        return this.blacklisted;
    }

    public int getPagesKnown() {
        return this.pagesKnown;
    }

    public int getPagesFetched() {
        return this.pagesFetched;
    }

    public int getPagesIndexed() {
        return this.pagesIndexed;
    }

    public int getIncomingLinks() {
        return this.incomingLinks;
    }

    public int getOutboundLinks() {
        return this.outboundLinks;
    }

    public int getNodeAffinity() {
        return this.nodeAffinity;
    }

    public double getRanking() {
        return this.ranking;
    }

    public boolean isSuggestForCrawling() {
        return this.suggestForCrawling;
    }

    public boolean isInCrawlQueue() {
        return this.inCrawlQueue;
    }

    public boolean isUnknownDomain() {
        return this.unknownDomain;
    }

    public String getIp() {
        return this.ip;
    }

    public Integer getAsn() {
        return this.asn;
    }

    public String getAsnOrg() {
        return this.asnOrg;
    }

    public String getAsnCountry() {
        return this.asnCountry;
    }

    public String getIpCountry() {
        return this.ipCountry;
    }

    public String getState() {
        return this.state;
    }

    public String toString() {
        return "DomainInformation(domain=" + this.getDomain() + ", blacklisted=" + this.isBlacklisted() + ", pagesKnown=" + this.getPagesKnown() + ", pagesFetched=" + this.getPagesFetched() + ", pagesIndexed=" + this.getPagesIndexed() + ", incomingLinks=" + this.getIncomingLinks() + ", outboundLinks=" + this.getOutboundLinks() + ", nodeAffinity=" + this.getNodeAffinity() + ", ranking=" + this.getRanking() + ", suggestForCrawling=" + this.isSuggestForCrawling() + ", inCrawlQueue=" + this.isInCrawlQueue() + ", unknownDomain=" + this.isUnknownDomain() + ", ip=" + this.getIp() + ", asn=" + this.getAsn() + ", asnOrg=" + this.getAsnOrg() + ", asnCountry=" + this.getAsnCountry() + ", ipCountry=" + this.getIpCountry() + ", state=" + this.getState() + ")";
    }

    public static class DomainInformationBuilder {
        private EdgeDomain domain;
        private boolean blacklisted;
        private int pagesKnown;
        private int pagesFetched;
        private int pagesIndexed;
        private int incomingLinks;
        private int outboundLinks;
        private int nodeAffinity;
        private double ranking;
        private boolean suggestForCrawling;
        private boolean inCrawlQueue;
        private boolean unknownDomain;
        private String ip;
        private Integer asn;
        private String asnOrg;
        private String asnCountry;
        private String ipCountry;
        private String state;

        DomainInformationBuilder() {
        }

        public DomainInformationBuilder domain(EdgeDomain domain) {
            this.domain = domain;
            return this;
        }

        public DomainInformationBuilder blacklisted(boolean blacklisted) {
            this.blacklisted = blacklisted;
            return this;
        }

        public DomainInformationBuilder pagesKnown(int pagesKnown) {
            this.pagesKnown = pagesKnown;
            return this;
        }

        public DomainInformationBuilder pagesFetched(int pagesFetched) {
            this.pagesFetched = pagesFetched;
            return this;
        }

        public DomainInformationBuilder pagesIndexed(int pagesIndexed) {
            this.pagesIndexed = pagesIndexed;
            return this;
        }

        public DomainInformationBuilder incomingLinks(int incomingLinks) {
            this.incomingLinks = incomingLinks;
            return this;
        }

        public DomainInformationBuilder outboundLinks(int outboundLinks) {
            this.outboundLinks = outboundLinks;
            return this;
        }

        public DomainInformationBuilder nodeAffinity(int nodeAffinity) {
            this.nodeAffinity = nodeAffinity;
            return this;
        }

        public DomainInformationBuilder ranking(double ranking) {
            this.ranking = ranking;
            return this;
        }

        public DomainInformationBuilder suggestForCrawling(boolean suggestForCrawling) {
            this.suggestForCrawling = suggestForCrawling;
            return this;
        }

        public DomainInformationBuilder inCrawlQueue(boolean inCrawlQueue) {
            this.inCrawlQueue = inCrawlQueue;
            return this;
        }

        public DomainInformationBuilder unknownDomain(boolean unknownDomain) {
            this.unknownDomain = unknownDomain;
            return this;
        }

        public DomainInformationBuilder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public DomainInformationBuilder asn(Integer asn) {
            this.asn = asn;
            return this;
        }

        public DomainInformationBuilder asnOrg(String asnOrg) {
            this.asnOrg = asnOrg;
            return this;
        }

        public DomainInformationBuilder asnCountry(String asnCountry) {
            this.asnCountry = asnCountry;
            return this;
        }

        public DomainInformationBuilder ipCountry(String ipCountry) {
            this.ipCountry = ipCountry;
            return this;
        }

        public DomainInformationBuilder state(String state) {
            this.state = state;
            return this;
        }

        public DomainInformation build() {
            return new DomainInformation(this.domain, this.blacklisted, this.pagesKnown, this.pagesFetched, this.pagesIndexed, this.incomingLinks, this.outboundLinks, this.nodeAffinity, this.ranking, this.suggestForCrawling, this.inCrawlQueue, this.unknownDomain, this.ip, this.asn, this.asnOrg, this.asnCountry, this.ipCountry, this.state);
        }

        public String toString() {
            return "DomainInformation.DomainInformationBuilder(domain=" + this.domain + ", blacklisted=" + this.blacklisted + ", pagesKnown=" + this.pagesKnown + ", pagesFetched=" + this.pagesFetched + ", pagesIndexed=" + this.pagesIndexed + ", incomingLinks=" + this.incomingLinks + ", outboundLinks=" + this.outboundLinks + ", nodeAffinity=" + this.nodeAffinity + ", ranking=" + this.ranking + ", suggestForCrawling=" + this.suggestForCrawling + ", inCrawlQueue=" + this.inCrawlQueue + ", unknownDomain=" + this.unknownDomain + ", ip=" + this.ip + ", asn=" + this.asn + ", asnOrg=" + this.asnOrg + ", asnCountry=" + this.asnCountry + ", ipCountry=" + this.ipCountry + ", state=" + this.state + ")";
        }
    }
}
