package nu.marginalia.api.model;


import java.util.EnumSet;

public record ApiLicense(
        String key,
        String license,
        String name,
        int ratePerMinute,
        int ratePerDay,
        EnumSet<ApiLicenseOptions> options
) {

    public boolean hasOption(ApiLicenseOptions option) {
        return options.contains(option);
    }

}
