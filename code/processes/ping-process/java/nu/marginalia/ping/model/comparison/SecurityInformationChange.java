package nu.marginalia.ping.model.comparison;

import nu.marginalia.ping.model.DomainAvailabilityRecord;
import nu.marginalia.ping.model.DomainSecurityRecord;
import nu.marginalia.ping.model.HttpSchema;
import nu.marginalia.ping.model.SchemaChange;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record SecurityInformationChange(
        boolean isChanged,
        boolean isAsnChanged,
        boolean isCertificateFingerprintChanged,
        boolean isCertificateProfileChanged,
        boolean isCertificateSanChanged,
        boolean isCertificatePublicKeyChanged,
        boolean isCertificateSerialNumberChanged,
        boolean isCertificateIssuerChanged,
        Duration oldCertificateTimeToExpiry,
        boolean isSecurityHeadersChanged,
        boolean isIpAddressChanged,
        boolean isSoftwareHeaderChanged,
        SchemaChange schemaChange
) {
    public static SecurityInformationChange between(
            DomainSecurityRecord before, DomainAvailabilityRecord availabilityBefore,
            DomainSecurityRecord after,  DomainAvailabilityRecord availabilityAfter
    ) {
        boolean asnChanged = !Objects.equals(before.asn(), after.asn());

        boolean ipChanged = 0 != Arrays.compare(availabilityBefore.serverIp(), availabilityAfter.serverIp());

        boolean certificateFingerprintChanged = 0 != Arrays.compare(before.sslCertFingerprintSha256(), after.sslCertFingerprintSha256());
        boolean certificateProfileChanged = before.certificateProfileHash() != after.certificateProfileHash();
        boolean certificateSerialNumberChanged = !Objects.equals(before.sslCertSerialNumber(), after.sslCertSerialNumber());
        boolean certificatePublicKeyChanged = 0 != Arrays.compare(before.sslCertPublicKeyHash(), after.sslCertPublicKeyHash());
        boolean certificateSanChanged =  !Objects.equals(before.sslCertSan(), after.sslCertSan());
        boolean certificateIssuerChanged = !Objects.equals(before.sslCertIssuer(), after.sslCertIssuer());

        Duration oldCertificateTimeToExpiry = before.sslCertNotAfter() == null ? null : Duration.between(
                Instant.now(),
                before.sslCertNotAfter()
        );

        boolean securityHeadersChanged = before.securityHeadersHash() != after.securityHeadersHash();
        boolean softwareChanged = !Objects.equals(before.headerServer(), after.headerServer());

        SchemaChange schemaChange = getSchemaChange(before, after);

        // Note we don't include IP address changes in the overall change status,
        // as this is not alone considered a change in security information; we may have
        // multiple IP addresses for a domain, and the IP address may change frequently
        // within the same ASN or certificate profile.

        boolean isChanged = asnChanged
                || certificateFingerprintChanged
                || securityHeadersChanged
                || certificateProfileChanged
                || softwareChanged
                || schemaChange.isSignificant();

        return new SecurityInformationChange(
                isChanged,
                asnChanged,
                certificateFingerprintChanged,
                certificateProfileChanged,
                certificateSanChanged,
                certificatePublicKeyChanged,
                certificateSerialNumberChanged,
                certificateIssuerChanged,
                oldCertificateTimeToExpiry,
                securityHeadersChanged,
                ipChanged,
                softwareChanged,
                schemaChange
        );
    }

    private static @NotNull SchemaChange getSchemaChange(DomainSecurityRecord before, DomainSecurityRecord after) {
        if (before.httpSchema() == null || after.httpSchema() == null) {
            return SchemaChange.UNKNOWN;
        }

        boolean beforeIsHttp = before.httpSchema() == HttpSchema.HTTP;
        boolean afterIsHttp = after.httpSchema() == HttpSchema.HTTP;
        boolean beforeIsHttps = before.httpSchema() == HttpSchema.HTTPS;
        boolean afterIsHttps = after.httpSchema() == HttpSchema.HTTPS;

        SchemaChange schemaChange;

        if (beforeIsHttp && afterIsHttp) {
            schemaChange = SchemaChange.NONE;
        } else if (beforeIsHttps && afterIsHttps) {
            schemaChange = SchemaChange.NONE;
        } else if (beforeIsHttp && afterIsHttps) {
            schemaChange = SchemaChange.HTTP_TO_HTTPS;
        } else if (beforeIsHttps && afterIsHttp) {
            schemaChange = SchemaChange.HTTPS_TO_HTTP;
        } else {
            schemaChange = SchemaChange.UNKNOWN;
        }
        return schemaChange;
    }


}
