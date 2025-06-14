package nu.marginalia.coordination;

import nu.marginalia.model.EdgeDomain;

public class DefaultDomainPermits {

    public static int defaultPermits(EdgeDomain domain) {
        return defaultPermits(domain.topDomain.toLowerCase());
    }

    public static int defaultPermits(String topDomain) {

        if (topDomain.equals("wordpress.com"))
            return 16;
        if (topDomain.equals("blogspot.com"))
            return 8;
        if (topDomain.equals("tumblr.com"))
            return 8;
        if (topDomain.equals("neocities.org"))
            return 8;
        if (topDomain.equals("github.io"))
            return 8;
        // Substack really dislikes broad-scale crawlers, so we need to be careful
        // to not get blocked.
        if (topDomain.equals("substack.com")) {
            return 1;
        }

        return 2;
    }

}
