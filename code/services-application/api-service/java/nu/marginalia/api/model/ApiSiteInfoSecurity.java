package nu.marginalia.api.model;

import org.jetbrains.annotations.Nullable;

public record ApiSiteInfoSecurity(
        @Nullable String sslVersion,
        @Nullable String httpVersion,
        boolean supportsCompression,
        @Nullable String certificateIssuer
) {}
