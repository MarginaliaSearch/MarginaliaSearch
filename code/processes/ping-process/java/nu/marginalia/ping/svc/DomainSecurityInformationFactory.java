package nu.marginalia.ping.svc;

import nu.marginalia.ping.fetcher.response.HttpResponse;
import nu.marginalia.ping.fetcher.response.HttpsResponse;
import nu.marginalia.ping.model.DomainSecurityRecord;
import nu.marginalia.ping.model.HttpSchema;
import nu.marginalia.ping.ssl.PKIXValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

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
//                .headerContentSecurityPolicy(headers.getFirst("Content-Security-Policy").getBytes())
                .httpCompression(headers.getFirst("Content-Encoding"))
                .httpCacheControl(headers.getFirst("Cache-Control"))
                .headerXPoweredBy(headers.getFirst("X-Powered-By"))
                .tsLastUpdate(Instant.now())
                .build();
    }

    // HTTPS response
    public DomainSecurityRecord createHttpsSecurityInformation(
            HttpsResponse httpResponse,
            PKIXValidationResult validationResult,
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
                for (var sanEntry : sslCertificates[0].getSubjectAlternativeNames()) {


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


        String keyExchange = getKeyExchange(sslCertificates[0]);

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
//                .headerContentSecurityPolicy(headers.getFirst("Content-Security-Policy").getBytes())
                .httpCompression(headers.getFirst("Content-Encoding"))
                .httpCacheControl(headers.getFirst("Cache-Control"))
                .headerXPoweredBy(headers.getFirst("X-Powered-By"))
                .sslProtocol(metadata.protocol())
                .sslCipherSuite(metadata.cipherSuite())
                .sslKeyExchange(keyExchange)
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
            return new byte[0]; // Re-throw to handle it upstream
        }
    }

    private byte[] getFingerprint(X509Certificate sslCertificate) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(sslCertificate.getEncoded());
        }
        catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            logger.warn("Failed to calculate certificate fingerprint: {}", e.getMessage());
            return new byte[0]; // Re-throw to handle it upstream
        }
    }

    private String getKeyExchange(X509Certificate cert) {
        StringJoiner keyExchanges = new StringJoiner(", ");
        Set<String> keyUsages = getKeyUsage(cert);
        String algorithm = cert.getPublicKey().getAlgorithm();

        boolean supportsPFS = false; // Perfect Forward Secrecy
        if ("RSA".equals(algorithm)) {
            if (keyUsages.contains("keyEncipherment")) {
                keyExchanges.add("RSA");
            }
            if (keyUsages.contains("digitalSignature")) {
                keyExchanges.add("DHE_RSA");
                keyExchanges.add("ECDHE_RSA");
            }
        } else if ("EC".equals(algorithm)) {
            if (keyUsages.contains("digitalSignature")) {
                keyExchanges.add("ECDHE_ECDSA");
            }
            if (keyUsages.contains("keyAgreement")) {
                keyExchanges.add("ECDH_ECDSA");
            }
        }

        return keyExchanges.toString();
    }

    public static Set<String> getKeyUsage(X509Certificate cert) {
        boolean[] keyUsage = cert.getKeyUsage();
        Set<String> usages = new HashSet<>();

        if (keyUsage != null) {
            String[] names = {
                    "digitalSignature", "nonRepudiation", "keyEncipherment",
                    "dataEncipherment", "keyAgreement", "keyCertSign",
                    "cRLSign", "encipherOnly", "decipherOnly"
            };

            for (int i = 0; i < keyUsage.length && i < names.length; i++) {
                if (keyUsage[i]) {
                    usages.add(names[i]);
                }
            }
        }

        return usages;
    }

}
