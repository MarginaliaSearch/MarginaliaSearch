package nu.marginalia.converting.sideload.dirtree;

import java.util.List;

class DirtreeSideloadSpec {
    public String name;
    public String domainName;
    public String dir;
    public String baseUrl;
    public List<String> keywords;

    public DirtreeSideloadSpec(String name, String domainName, String dir, String baseUrl, List<String> keywords) {
        this.name = name;
        this.domainName = domainName;
        this.dir = dir;
        this.baseUrl = baseUrl;
        this.keywords = keywords;
    }

    public DirtreeSideloadSpec() {
    }

    public String getName() {
        return this.name;
    }

    public String getDomainName() {
        return this.domainName;
    }

    public String getDir() {
        return this.dir;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public List<String> getKeywords() {
        return this.keywords;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public void setDir(String dir) {
        this.dir = dir;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}
