package nu.marginalia.ping.model.comparison;

import nu.marginalia.ping.model.DomainAvailabilityRecord;
import nu.marginalia.ping.model.DomainSecurityRecord;

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
        boolean isSoftwareHeaderChanged
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

        // Note we don't include IP address changes in the overall change status,
        // as this is not alone considered a change in security information; we may have
        // multiple IP addresses for a domain, and the IP address may change frequently
        // within the same ASN or certificate profile.

        boolean isChanged = asnChanged
                || certificateFingerprintChanged
                || securityHeadersChanged
                || certificateProfileChanged
                || softwareChanged;

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
                softwareChanged
        );
    }


}
