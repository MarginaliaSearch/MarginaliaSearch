package nu.marginalia.converting.processor.logic.links;

import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.ip_blocklist.UrlBlocklist;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class LinkProcessor {
    private final ProcessedDocumentDetails ret;
    private final EdgeUrl baseUrl;

    private final Set<EdgeUrl> nonIndexable = new HashSet<>();

    private final Set<EdgeUrl> seenUrls = new HashSet<>();
    private final Set<EdgeDomain> foreignDomains = new HashSet<>();

    private static final int MAX_INTERNAL_LINK = 250;
    private static final int MAX_EXTERNAL_LINK = 100;
    private static final UrlBlocklist urlBlocklist = new UrlBlocklist();

    public LinkProcessor(ProcessedDocumentDetails documentDetails, EdgeUrl baseUrl) {
        this.ret = documentDetails;
        this.baseUrl = baseUrl;

        ret.linksExternal = new ArrayList<>();
        ret.linksInternal = new ArrayList<>();
        ret.feedLinks = new ArrayList<>();
    }

    public Set<EdgeDomain> getForeignDomains() {
        return foreignDomains;
    }
    
    public Set<EdgeUrl> getNonIndexableUrls() {
        return nonIndexable;
    }

    public void accept(EdgeUrl link) {
        if (!isLinkPermitted(link)) {
            return;
        }

        if (!seenUrls.add(link)) {
            return;
        }

        if (Objects.equals(link.domain, baseUrl.domain)) { // internal link
            if (ret.linksInternal.size() < MAX_INTERNAL_LINK) {
                ret.linksInternal.add(link);
            }
        }
        else {
            if (ret.linksExternal.size() < MAX_EXTERNAL_LINK) {
                ret.linksExternal.add(link);
                foreignDomains.add(link.domain);
            }
        }
    }

    public void acceptFeed(EdgeUrl link) {
        if (!isLinkPermitted(link)) {
            return;
        }

        if (!seenUrls.add(link)) {
            return;
        }

        ret.feedLinks.add(link);
    }

    private boolean isLinkPermitted(EdgeUrl link) {
        if (!isProtoSupported(link.proto)) {
            return false;
        }

        if (urlBlocklist.isMailingListLink(link)) {
            return false;
        }

        if (urlBlocklist.isUrlBlocked(link)) {
            return false;
        }

        return true;
    }

    private boolean isProtoSupported(String proto) {
        return proto.equalsIgnoreCase("http")
            || proto.equalsIgnoreCase("https");
    }

    public void acceptNonIndexable(EdgeUrl edgeUrl) {
        nonIndexable.add(edgeUrl);
    }
}
