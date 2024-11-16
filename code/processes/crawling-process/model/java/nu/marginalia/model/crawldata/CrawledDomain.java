package nu.marginalia.model.crawldata;

import java.util.List;

public class CrawledDomain implements SerializableCrawlData {
    public String domain;

    public String redirectDomain;

    public String crawlerStatus;
    public String crawlerStatusDesc;
    public String ip;

    public List<CrawledDocument> doc;

    /**
     * This is not guaranteed to be set in all versions of the format,
     * information may come in CrawledDocument instead
     */
    public List<String> cookies;

    public CrawledDomain(String domain, String redirectDomain, String crawlerStatus, String crawlerStatusDesc, String ip, List<CrawledDocument> doc, List<String> cookies) {
        this.domain = domain;
        this.redirectDomain = redirectDomain;
        this.crawlerStatus = crawlerStatus;
        this.crawlerStatusDesc = crawlerStatusDesc;
        this.ip = ip;
        this.doc = doc;
        this.cookies = cookies;
    }

    public static CrawledDomainBuilder builder() {
        return new CrawledDomainBuilder();
    }

    public int size() {
        if (doc == null) return 0;
        return doc.size();
    }

    public String getDomain() {
        return this.domain;
    }

    public String getRedirectDomain() {
        return this.redirectDomain;
    }

    public String getCrawlerStatus() {
        return this.crawlerStatus;
    }

    public String getCrawlerStatusDesc() {
        return this.crawlerStatusDesc;
    }

    public String getIp() {
        return this.ip;
    }

    public List<CrawledDocument> getDoc() {
        return this.doc;
    }

    public List<String> getCookies() {
        return this.cookies;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public void setRedirectDomain(String redirectDomain) {
        this.redirectDomain = redirectDomain;
    }

    public void setCrawlerStatus(String crawlerStatus) {
        this.crawlerStatus = crawlerStatus;
    }

    public void setCrawlerStatusDesc(String crawlerStatusDesc) {
        this.crawlerStatusDesc = crawlerStatusDesc;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setDoc(List<CrawledDocument> doc) {
        this.doc = doc;
    }

    public void setCookies(List<String> cookies) {
        this.cookies = cookies;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof CrawledDomain)) return false;
        final CrawledDomain other = (CrawledDomain) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$domain = this.getDomain();
        final Object other$domain = other.getDomain();
        if (this$domain == null ? other$domain != null : !this$domain.equals(other$domain)) return false;
        final Object this$redirectDomain = this.getRedirectDomain();
        final Object other$redirectDomain = other.getRedirectDomain();
        if (this$redirectDomain == null ? other$redirectDomain != null : !this$redirectDomain.equals(other$redirectDomain))
            return false;
        final Object this$crawlerStatus = this.getCrawlerStatus();
        final Object other$crawlerStatus = other.getCrawlerStatus();
        if (this$crawlerStatus == null ? other$crawlerStatus != null : !this$crawlerStatus.equals(other$crawlerStatus))
            return false;
        final Object this$crawlerStatusDesc = this.getCrawlerStatusDesc();
        final Object other$crawlerStatusDesc = other.getCrawlerStatusDesc();
        if (this$crawlerStatusDesc == null ? other$crawlerStatusDesc != null : !this$crawlerStatusDesc.equals(other$crawlerStatusDesc))
            return false;
        final Object this$ip = this.getIp();
        final Object other$ip = other.getIp();
        if (this$ip == null ? other$ip != null : !this$ip.equals(other$ip)) return false;
        final Object this$doc = this.getDoc();
        final Object other$doc = other.getDoc();
        if (this$doc == null ? other$doc != null : !this$doc.equals(other$doc)) return false;
        final Object this$cookies = this.getCookies();
        final Object other$cookies = other.getCookies();
        if (this$cookies == null ? other$cookies != null : !this$cookies.equals(other$cookies)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof CrawledDomain;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $domain = this.getDomain();
        result = result * PRIME + ($domain == null ? 43 : $domain.hashCode());
        final Object $redirectDomain = this.getRedirectDomain();
        result = result * PRIME + ($redirectDomain == null ? 43 : $redirectDomain.hashCode());
        final Object $crawlerStatus = this.getCrawlerStatus();
        result = result * PRIME + ($crawlerStatus == null ? 43 : $crawlerStatus.hashCode());
        final Object $crawlerStatusDesc = this.getCrawlerStatusDesc();
        result = result * PRIME + ($crawlerStatusDesc == null ? 43 : $crawlerStatusDesc.hashCode());
        final Object $ip = this.getIp();
        result = result * PRIME + ($ip == null ? 43 : $ip.hashCode());
        final Object $doc = this.getDoc();
        result = result * PRIME + ($doc == null ? 43 : $doc.hashCode());
        final Object $cookies = this.getCookies();
        result = result * PRIME + ($cookies == null ? 43 : $cookies.hashCode());
        return result;
    }

    public String toString() {
        return "CrawledDomain(domain=" + this.getDomain() + ", redirectDomain=" + this.getRedirectDomain() + ", crawlerStatus=" + this.getCrawlerStatus() + ", crawlerStatusDesc=" + this.getCrawlerStatusDesc() + ", ip=" + this.getIp() + ", doc=" + this.getDoc() + ", cookies=" + this.getCookies() + ")";
    }

    public static class CrawledDomainBuilder {
        private String domain;
        private String redirectDomain;
        private String crawlerStatus;
        private String crawlerStatusDesc;
        private String ip;
        private List<CrawledDocument> doc;
        private List<String> cookies;

        CrawledDomainBuilder() {
        }

        public CrawledDomainBuilder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public CrawledDomainBuilder redirectDomain(String redirectDomain) {
            this.redirectDomain = redirectDomain;
            return this;
        }

        public CrawledDomainBuilder crawlerStatus(String crawlerStatus) {
            this.crawlerStatus = crawlerStatus;
            return this;
        }

        public CrawledDomainBuilder crawlerStatusDesc(String crawlerStatusDesc) {
            this.crawlerStatusDesc = crawlerStatusDesc;
            return this;
        }

        public CrawledDomainBuilder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public CrawledDomainBuilder doc(List<CrawledDocument> doc) {
            this.doc = doc;
            return this;
        }

        public CrawledDomainBuilder cookies(List<String> cookies) {
            this.cookies = cookies;
            return this;
        }

        public CrawledDomain build() {
            return new CrawledDomain(this.domain, this.redirectDomain, this.crawlerStatus, this.crawlerStatusDesc, this.ip, this.doc, this.cookies);
        }

        public String toString() {
            return "CrawledDomain.CrawledDomainBuilder(domain=" + this.domain + ", redirectDomain=" + this.redirectDomain + ", crawlerStatus=" + this.crawlerStatus + ", crawlerStatusDesc=" + this.crawlerStatusDesc + ", ip=" + this.ip + ", doc=" + this.doc + ", cookies=" + this.cookies + ")";
        }
    }
}
