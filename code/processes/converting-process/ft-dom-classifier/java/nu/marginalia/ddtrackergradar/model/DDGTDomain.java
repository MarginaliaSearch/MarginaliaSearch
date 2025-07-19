package nu.marginalia.ddtrackergradar.model;

import java.util.List;

public record DDGTDomain(
        String domain,
        DDGTOwner owner,
        List<String> categories,
        List<String> subdomains
)
{
}
