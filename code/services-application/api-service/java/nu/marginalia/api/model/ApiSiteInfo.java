package nu.marginalia.api.model;

public record ApiSiteInfo(
        String domain,
        String state,
        boolean blacklisted,
        ApiSiteInfoPing ping,
        ApiSiteInfoSecurity security
) {}
