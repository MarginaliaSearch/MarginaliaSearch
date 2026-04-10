package nu.marginalia.ping.svc;

import nu.marginalia.ping.fetcher.response.HttpResponse;
import nu.marginalia.ping.fetcher.response.HttpsResponse;
import nu.marginalia.ping.model.DomainSecurityRecord;
import nu.marginalia.ping.model.HttpSchema;
import nu.marginalia.ping.ssl.CertificateValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;

public class DomainSecurityInformationFactory {
    private static final Logger logger = LoggerFactory.getLogger(DomainSecurityInformationFactory.class);

    // Vanilla HTTP (not HTTPS) response does not have SSL session information, so we return null
    public DomainSecurityRecord createHttpSecurityInformation(HttpResponse httpResponse,
                                                              int domainId, int nodeId,
                                                              @Nullable Integer asn
                                                              ) {

        var headers = httpResponse.headers();

        return DomainSecurityRecord.builder()
                .domainId(domainId)
                .nodeId(nodeId)
                .asn(asn)
                .httpSchema(HttpSchema.HTTP)
                .httpVersion(httpResponse.version())
                .headerServer(headers.getFirst("Server"))
                .headerCorsAllowOrigin(headers.getFirst("Access-Control-Allow-Origin"))
                .headerCorsAllowCredentials("true".equals(headers.getFirst("Access-Control-Allow-Credentials")))
                .headerXContentTypeOptions(headers.getFirst("X-Content-Type-Options"))
                .headerXFrameOptions(headers.getFirst("X-Frame-Options"))
                .headerXXssProtection(headers.getFirst("X-XSS-Protection"))
                .headerReferrerPolicy(headers.getFirst("Referrer-Policy"))
                .headerStrictTransportSecurity(headers.getFirst("Strict-Transport-Security"))
                .headerContentSecurityPolicyHash(Objects.requireNonNullElse(headers.getFirst("Content-Security-Policy"),"").hashCode())
                .httpCompression(headers.getFirst("Content-Encoding"))
                .httpCacheControl(headers.getFirst("Cache-Control"))
                .headerXPoweredBy(headers.getFirst("X-Powered-By"))
                .tsLastUpdate(Instant.now())
                .build();
    }

    // HTTPS response
    public DomainSecurityRecord createHttpsSecurityInformation(
            HttpsResponse httpResponse,
            CertificateValidator.ValidationResult validationResult,
            int domainId,
            int nodeId,
            @Nullable Integer asn
            ) {


        var headers = httpResponse.headers();
        var metadata = httpResponse.sslMetadata();
        var sslCertificates = (X509Certificate[]) httpResponse.sslCertificates();

        StringJoiner san = new StringJoiner(", ");
        boolean isWildcard = false;
        try {
            if (sslCertificates != null && sslCertificates.length > 0) {
                Collection<List<?>> sans = sslCertificates[0].getSubjectAlternativeNames();
                if (sans == null) {
                    sans = Collections.emptyList();
                }
                for (var sanEntry : sans) {

                    if (sanEntry != null && sanEntry.size() >= 2) {
                        // Check if the SAN entry is a DNS or IP address
                        int type = (Integer) sanEntry.get(0);
                        String value = (String) sanEntry.get(1);
                        if (type == 2 || type == 7) { // DNS or IP SAN
                            san.add(value);
                        }
                        if (type == 2 && value.startsWith("*.")) {
                            isWildcard = true;
                        }
                    }

                }
            }
        }
        catch (Exception e) {
            logger.warn("Failed to get SAN from certificate: {}", e.getMessage());
        }

        return DomainSecurityRecord.builder()
                .domainId(domainId)
                .nodeId(nodeId)
                .asn(asn)
                .httpSchema(HttpSchema.HTTPS)
                .headerServer(headers.getFirst("Server"))
                .headerCorsAllowOrigin(headers.getFirst("Access-Control-Allow-Origin"))
                .headerCorsAllowCredentials("true".equals(headers.getFirst("Access-Control-Allow-Credentials")))
                .headerXContentTypeOptions(headers.getFirst("X-Content-Type-Options"))
                .headerXFrameOptions(headers.getFirst("X-Frame-Options"))
                .headerXXssProtection(headers.getFirst("X-XSS-Protection"))
                .headerReferrerPolicy(headers.getFirst("Referrer-Policy"))
                .headerStrictTransportSecurity(headers.getFirst("Strict-Transport-Security"))
                .headerContentSecurityPolicyHash(Objects.requireNonNullElse(headers.getFirst("Content-Security-Policy"),"").hashCode())
                .httpCompression(headers.getFirst("Content-Encoding"))
                .httpCacheControl(headers.getFirst("Cache-Control"))
                .headerXPoweredBy(headers.getFirst("X-Powered-By"))
                .sslProtocol(metadata.protocol())
                .sslCipherSuite(metadata.cipherSuite())
                .sslKeyExchange(getPossibleKeyExchanges(sslCertificates[0]))
                .sslCertNotBefore(sslCertificates[0].getNotBefore().toInstant())
                .sslCertNotAfter(sslCertificates[0].getNotAfter().toInstant())
                .sslCertIssuer(sslCertificates[0].getIssuerX500Principal().getName())
                .sslCertSubject(sslCertificates[0].getSubjectX500Principal().getName())
                .sslCertSerialNumber(sslCertificates[0].getSerialNumber().toString())
                .sslCertFingerprintSha256(getFingerprint(sslCertificates[0]))
                .sslCertPublicKeyHash(getPublicKeyHash(sslCertificates[0]))
                .sslCertSan(san.length() > 0 ? san.toString() : null)
                .sslCertWildcard(isWildcard)
                .sslCertificateChainLength(sslCertificates.length)
                .sslCertificateValid(validationResult.isValid())
                .sslHostValid(validationResult.hostnameValid)
                .sslChainValid(validationResult.chainValid)
                .sslDateValid(!validationResult.certificateExpired)
                .httpVersion(httpResponse.version())
                .tsLastUpdate(Instant.now())
                .build();
    }

    private byte[] getPublicKeyHash(X509Certificate sslCertificate) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(sslCertificate.getPublicKey().getEncoded());
        }
        catch (NoSuchAlgorithmException e) {
            logger.warn("Failed to calculate public key hash: {}", e.getMessage());
            return new byte[0];
        }
    }

    private byte[] getFingerprint(X509Certificate sslCertificate) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(sslCertificate.getEncoded());
        }
        catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            logger.warn("Failed to calculate certificate fingerprint: {}", e.getMessage());
            return new byte[0];
        }
    }

    // https://datatracker.ietf.org/doc/html/rfc5280#section-4.2.1.3
    private static final int KU_DIGITAL_SIGNATURE = 0;
    private static final int KU_KEY_ENCIPHERMENT = 2;
    private static final int KU_KEY_AGREEMENT = 4;

    private String getPossibleKeyExchanges(X509Certificate cert) {
        boolean[] keyUsages = cert.getKeyUsage();
        if (keyUsages == null) return "";

        StringJoiner keyExchanges = new StringJoiner(", ");
        String algorithm = cert.getPublicKey().getAlgorithm();

        if ("RSA".equals(algorithm)) {
            if (keyUsages.length > KU_KEY_ENCIPHERMENT && keyUsages[KU_KEY_ENCIPHERMENT]) {
                keyExchanges.add("RSA");
            }
            if (keyUsages.length > KU_DIGITAL_SIGNATURE && keyUsages[KU_DIGITAL_SIGNATURE]) {
                keyExchanges.add("DHE_RSA");
                keyExchanges.add("ECDHE_RSA");
            }
        } else if ("EC".equals(algorithm)) {
            if (keyUsages.length > KU_DIGITAL_SIGNATURE && keyUsages[KU_DIGITAL_SIGNATURE]) {
                keyExchanges.add("ECDHE_ECDSA");
            }
            if (keyUsages.length > KU_KEY_AGREEMENT && keyUsages[KU_KEY_AGREEMENT]) {
                keyExchanges.add("ECDH_ECDSA");
            }
        }

        return keyExchanges.toString();
    }

}
