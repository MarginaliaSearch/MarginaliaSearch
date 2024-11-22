package nu.marginalia.model.crawldata;

import java.util.List;
import java.util.Objects;

public final class CrawledDomain implements SerializableCrawlData {
    public String domain;

    public String redirectDomain;

    public String crawlerStatus;
    public String crawlerStatusDesc;
    public String ip;

    @Deprecated // This used to be populated, but is no longer
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CrawledDomain that)) return false;

        return Objects.equals(domain, that.domain) && Objects.equals(redirectDomain, that.redirectDomain) && Objects.equals(crawlerStatus, that.crawlerStatus) && Objects.equals(crawlerStatusDesc, that.crawlerStatusDesc) && Objects.equals(ip, that.ip) && Objects.equals(doc, that.doc) && Objects.equals(cookies, that.cookies);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(domain);
        result = 31 * result + Objects.hashCode(redirectDomain);
        result = 31 * result + Objects.hashCode(crawlerStatus);
        result = 31 * result + Objects.hashCode(crawlerStatusDesc);
        result = 31 * result + Objects.hashCode(ip);
        result = 31 * result + Objects.hashCode(doc);
        result = 31 * result + Objects.hashCode(cookies);
        return result;
    }

    public String toString() {
        return "CrawledDomain(domain=" + this.getDomain() + ", redirectDomain=" + this.getRedirectDomain() + ", crawlerStatus=" + this.getCrawlerStatus() + ", crawlerStatusDesc=" + this.getCrawlerStatusDesc() + ", ip=" + this.getIp() + ", doc=" + this.getDoc() + ", cookies=" + this.getCookies() + ")";
    }
}
