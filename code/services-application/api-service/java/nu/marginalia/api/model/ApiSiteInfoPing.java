package nu.marginalia.api.model;

import org.jetbrains.annotations.Nullable;

public record ApiSiteInfoPing(
        boolean serverAvailable,
        String schema,
        int avgResponseTimeMs,
        @Nullable String lastChecked,
        @Nullable String errorMessage
) {}
