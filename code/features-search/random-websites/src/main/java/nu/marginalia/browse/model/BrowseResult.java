package nu.marginalia.browse.model;

import nu.marginalia.model.EdgeUrl;

public record BrowseResult (EdgeUrl url,
                            int domainId,
                            double relatedness,
                            boolean indexed) {

    public String domainHash() {
        var domain = url.domain;
        if ("www".equals(domain.subDomain)) {
            return domain.domain;
        }
        return domain.toString();
    }

    public String displayDomain() {
        String ret;
        var domain = url.domain;
        if ("www".equals(domain.subDomain)) {
            ret = domain.domain;
        }
        else {
            ret = domain.toString();
        }
        if (ret.length() > 25) {
            ret = ret.substring(0, 22) + "...";
        }
        return ret;

    }
}
