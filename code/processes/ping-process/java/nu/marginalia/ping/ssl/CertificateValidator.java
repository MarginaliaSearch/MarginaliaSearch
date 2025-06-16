package nu.marginalia.ping.ssl;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.*;

import javax.security.auth.x500.X500Principal;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.*;

/** Utility class for validating X.509 certificates.
 * This class provides methods to validate certificate chains, check expiration,
 * hostname validity, and revocation status.
 * <p></p>
 * This is extremely unsuitable for actual SSL/TLS validation,
 * and is only to be used in analyzing certificates for fingerprinting
 * and diagnosing servers!
 */
public class CertificateValidator {
    public static class ValidationResult {
        public boolean chainValid = false;
        public boolean certificateExpired = false;
        public boolean certificateRevoked = false;
        public boolean selfSigned = false;
        public boolean hostnameValid = false;

        public boolean isValid() {
            return !selfSigned && !certificateExpired && !certificateRevoked && hostnameValid;
        }

        public List<String> errors = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public Map<String, Object> details = new HashMap<>();

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== Certificate Validation Result ===\n");
            sb.append("Chain Valid: ").append(chainValid ? "✓" : "✗").append("\n");
            sb.append("Not Expired: ").append(!certificateExpired ? "✓" : "✗").append("\n");
            sb.append("Not Revoked: ").append(!certificateRevoked ? "✓" : "✗").append("\n");
            sb.append("Hostname Valid: ").append(hostnameValid ? "✓" : "✗").append("\n");
            sb.append("Self-Signed: ").append(selfSigned ? "✓" : "✗").append("\n");

            if (!errors.isEmpty()) {
                sb.append("\nErrors:\n");
                for (String error : errors) {
                    sb.append("  ✗ ").append(error).append("\n");
                }
            }

            if (!warnings.isEmpty()) {
                sb.append("\nWarnings:\n");
                for (String warning : warnings) {
                    sb.append("  ⚠ ").append(warning).append("\n");
                }
            }

            if (!details.isEmpty()) {
                sb.append("\nDetails:\n");
                for (Map.Entry<String, Object> entry : details.entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }
            }

            return sb.toString();
        }
    }

    public static ValidationResult validateCertificate(X509Certificate[] certChain,
                                                       String hostname) {
        return validateCertificate(certChain, hostname, false);
    }

    public static ValidationResult validateCertificate(X509Certificate[] certChain,
                                                       String hostname,
                                                       boolean autoTrustFetchedRoots) {
        ValidationResult result = new ValidationResult();

        if (certChain == null || certChain.length == 0) {
            result.errors.add("No certificates provided");
            return result;
        }

        X509Certificate leafCert = certChain[0];

        // 1. Check certificate expiration
        result.certificateExpired = checkExpiration(leafCert, result);

        // 2. Check hostname validity
        result.hostnameValid = checkHostname(leafCert, hostname, result);

        result.selfSigned = certChain.length <= 1;

        // 3. Check certificate chain validity (with AIA fetching)
        result.chainValid = checkChainValidity(certChain, RootCerts.getTrustAnchors(), result, autoTrustFetchedRoots);

        // 4. Check revocation status
        result.certificateRevoked = checkRevocation(leafCert, result);

        return result;
    }

    private static boolean checkExpiration(X509Certificate cert, ValidationResult result) {
        try {
            cert.checkValidity();
            result.details.put("validFrom", cert.getNotBefore());
            result.details.put("validTo", cert.getNotAfter());

            // Warn if expires soon (30 days)
            long daysUntilExpiry = (cert.getNotAfter().getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
            if (daysUntilExpiry < 30) {
                result.warnings.add("Certificate expires in " + daysUntilExpiry + " days");
            }

            return false; // Not expired
        } catch (Exception e) {
            result.errors.add("Certificate expired or not yet valid: " + e.getMessage());
            return true; // Expired
        }
    }

    private static boolean checkHostname(X509Certificate cert, String hostname, ValidationResult result) {
        if (hostname == null || hostname.isEmpty()) {
            result.warnings.add("No hostname provided for validation");
            return false;
        }

        try {
            // Check Subject CN
            String subjectCN = getCommonName(cert.getSubjectX500Principal());
            if (subjectCN != null && matchesHostname(subjectCN, hostname)) {
                result.details.put("hostnameMatchedBy", "Subject CN: " + subjectCN);
                return true;
            }

            // Check Subject Alternative Names
            Collection<List<?>> subjectAltNames = cert.getSubjectAlternativeNames();
            if (subjectAltNames != null) {
                for (List<?> altName : subjectAltNames) {
                    if (altName.size() >= 2) {
                        Integer type = (Integer) altName.get(0);
                        if (type == 2) { // DNS name
                            String dnsName = (String) altName.get(1);
                            if (matchesHostname(dnsName, hostname)) {
                                result.details.put("hostnameMatchedBy", "SAN DNS: " + dnsName);
                                return true;
                            }
                        }
                    }
                }
            }

            result.errors.add("Hostname '" + hostname + "' does not match certificate");
            result.details.put("subjectCN", subjectCN);
            result.details.put("subjectAltNames", subjectAltNames);
            return false;

        } catch (Exception e) {
            result.errors.add("Error checking hostname: " + e.getMessage());
            return false;
        }
    }

    private static boolean checkChainValidity(X509Certificate[] originalChain,
                                              Set<TrustAnchor> trustAnchors,
                                              ValidationResult result,
                                              boolean autoTrustFetchedRoots) {
        try {
            // First try with the original chain
            ChainValidationResult originalResult = validateChain(originalChain, trustAnchors);

            if (originalResult.isValid) {
                result.details.put("chainLength", originalChain.length);
                result.details.put("chainExtended", false);
                return true;
            }

            try {
                List<X509Certificate> repairedChain = CertificateFetcher.buildCompleteChain(originalChain[0]);

                if (!repairedChain.isEmpty()) {

                    X509Certificate[] extendedArray = repairedChain.toArray(new X509Certificate[0]);

                    // Create a copy of trust anchors for potential modification
                    Set<TrustAnchor> workingTrustAnchors = new HashSet<>(trustAnchors);

                    // If auto-trust is enabled, add any self-signed certs as trusted roots
                    if (autoTrustFetchedRoots) {
                        for (X509Certificate cert : extendedArray) {
                            if (cert.getSubjectX500Principal().equals(cert.getIssuerX500Principal())) {
                                // Self-signed certificate - add to trust anchors if not already there
                                boolean alreadyTrusted = false;
                                for (TrustAnchor anchor : workingTrustAnchors) {
                                    if (anchor.getTrustedCert().equals(cert)) {
                                        alreadyTrusted = true;
                                        break;
                                    }
                                }
                                if (!alreadyTrusted) {
                                    workingTrustAnchors.add(new TrustAnchor(cert, null));
                                    result.warnings.add("Auto-trusted fetched root: " + cert.getSubjectX500Principal().getName());
                                }
                            }
                        }
                    }

                    ChainValidationResult extendedResult = validateChain(extendedArray, workingTrustAnchors);

                    result.details.put("chainLength", extendedArray.length);
                    result.details.put("originalChainLength", originalChain.length);
                    result.details.put("chainExtended", true);
                    result.details.put("fetchedIntermediates", extendedArray.length);
                    result.details.put("autoTrustedRoots", autoTrustFetchedRoots);

                    if (extendedResult.isValid) {
                        result.warnings.add("Extended certificate chain with " + extendedArray.length + " fetched intermediates");
                        return true;
                    } else {
                        result.errors.addAll(extendedResult.issues);
                        return false;
                    }
                } else {
                    result.warnings.add("Could not fetch missing intermediate certificates");
                    result.details.put("chainLength", originalChain.length);
                    result.details.put("chainExtended", false);
                    result.errors.addAll(originalResult.issues);
                    return false;
                }

            } catch (Exception e) {
                result.warnings.add("Failed to fetch intermediates: " + e.getMessage());
                result.details.put("chainLength", originalChain.length);
                result.details.put("chainExtended", false);
                result.errors.addAll(originalResult.issues);
                return false;
            }

        } catch (Exception e) {
            result.errors.add("Error validating chain: " + e.getMessage());
            return false;
        }
    }


    private static void debugCertificateChain(List<X509Certificate> certs, Set<TrustAnchor> trustAnchors) {
        System.out.println("=== Certificate Chain Analysis ===");

        int length = certs.size();
        System.out.println("Chain length: " + length);

        int i = 0;
        for (var x509cert : certs) {
            System.out.println("\nCertificate " + i++ + ":");
            System.out.println("  Subject: " + x509cert.getSubjectDN().getName());
            System.out.println("  Issuer:  " + x509cert.getIssuerDN().getName());
            System.out.println("  Serial:  " + x509cert.getSerialNumber().toString(16));
            System.out.println("  Valid:   " + x509cert.getNotBefore() + " to " + x509cert.getNotAfter());
            System.out.println("  Self-signed: " + x509cert.getSubjectDN().equals(x509cert.getIssuerDN()));

            // Check if we have the issuer in our trust anchors
            boolean issuerFound = false;
            for (TrustAnchor anchor : trustAnchors) {
                if (anchor.getTrustedCert().getSubjectDN().equals(x509cert.getIssuerDN())) {
                    issuerFound = true;
                    System.out.println("  Issuer found in trust anchors: " + anchor.getTrustedCert().getSubjectDN().getName());
                    break;
                }
            }
            if (!issuerFound && i == length) {
                System.out.println("  *** MISSING ISSUER: " + x509cert.getIssuerDN().getName());
            }
        }
    }

    private static class ChainValidationResult {
        boolean isValid = false;
        List<String> issues = new ArrayList<>();
    }

    private static ChainValidationResult validateChain(X509Certificate[] certChain, Set<TrustAnchor> trustAnchors) {
        ChainValidationResult result = new ChainValidationResult();

        // Check each certificate in the chain
        for (int i = 0; i < certChain.length; i++) {
            X509Certificate cert = certChain[i];

            // Check certificate validity dates
            try {
                cert.checkValidity();
            } catch (Exception e) {
                result.issues.add("Certificate " + i + " expired: " + cert.getSubjectDN());
            }

            // Check signature (except for self-signed root)
            if (i < certChain.length - 1) {
                X509Certificate issuer = certChain[i + 1];
                try {
                    cert.verify(issuer.getPublicKey());
                } catch (Exception e) {
                    result.issues.add("Certificate " + i + " signature invalid: " + e.getMessage());
                }

                // Check issuer/subject relationship
                if (!cert.getIssuerX500Principal().equals(issuer.getSubjectX500Principal())) {
                    result.issues.add("Certificate " + i + " issuer does not match certificate " + (i + 1) + " subject");
                }
            }
        }

        // Check if chain ends with a trusted root
        X509Certificate rootCert = certChain[certChain.length - 1];
        boolean trustedRootFound = false;

        if (rootCert.getSubjectX500Principal().equals(rootCert.getIssuerX500Principal())) {
            // Self-signed root - check if it's in trust anchors
            for (TrustAnchor anchor : trustAnchors) {
                if (anchor.getTrustedCert().equals(rootCert)) {
                    trustedRootFound = true;
                    break;
                }
            }

            if (!trustedRootFound) {
                // Check if we trust the root's subject even if the certificate is different
                for (TrustAnchor anchor : trustAnchors) {
                    if (anchor.getTrustedCert().getSubjectX500Principal().equals(rootCert.getSubjectX500Principal())) {
                        trustedRootFound = true;
                        // Note: we'll add this as a warning in the main result
                        break;
                    }
                }
            }
        } else {
            // Chain doesn't end with self-signed cert - check if issuer is trusted
            for (TrustAnchor anchor : trustAnchors) {
                if (anchor.getTrustedCert().getSubjectX500Principal().equals(rootCert.getIssuerX500Principal())) {
                    trustedRootFound = true;
                    break;
                }
            }
        }

        if (!trustedRootFound) {
            result.issues.add("Chain does not end with a trusted root");
        }

        result.isValid = result.issues.isEmpty();
        return result;
    }

    private static boolean checkRevocation(X509Certificate cert, ValidationResult result) {
        try {
            // Try OCSP first
            if (checkOCSP(cert, result)) {
                return true; // Revoked
            }

            // Fallback to CRL
            if (checkCRL(cert, result)) {
                return true; // Revoked
            }

            result.warnings.add("Could not check revocation status");
            return false; // Assume not revoked if we can't check

        } catch (Exception e) {
            result.warnings.add("Error checking revocation: " + e.getMessage());
            return false;
        }
    }

    private static boolean checkOCSP(X509Certificate cert, ValidationResult result) {
        // For now, just extract OCSP URL and note that we found it
        try {
            List<String> ocspUrls = CertificateFetcher.getOCSPUrls(cert);
            if (!ocspUrls.isEmpty()) {
                result.details.put("ocspUrls", ocspUrls);
                result.warnings.add("OCSP checking not implemented - found OCSP URLs: " + ocspUrls);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean checkCRL(X509Certificate cert, ValidationResult result) {
        // Basic CRL URL extraction
        try {
            List<String> crlUrls = getCRLUrls(cert);
            if (!crlUrls.isEmpty()) {
                result.details.put("crlUrls", crlUrls);
                result.warnings.add("CRL checking not implemented - found CRL URLs: " + crlUrls);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Helper methods
    private static String getCommonName(X500Principal principal) {
        String name = principal.getName();
        String[] parts = name.split(",");
        for (String part : parts) {
            part = part.trim();
            if (part.startsWith("CN=")) {
                return part.substring(3);
            }
        }
        return null;
    }

    private static boolean matchesHostname(String certName, String hostname) {
        if (certName == null || hostname == null) {
            return false;
        }

        // Exact match
        if (certName.equalsIgnoreCase(hostname)) {
            return true;
        }

        // Wildcard match
        if (certName.startsWith("*.")) {
            String certDomain = certName.substring(2);
            String hostDomain = hostname;
            int firstDot = hostname.indexOf('.');
            if (firstDot > 0) {
                hostDomain = hostname.substring(firstDot + 1);
            }
            return certDomain.equalsIgnoreCase(hostDomain);
        }

        return false;
    }

    private static List<String> getCRLUrls(X509Certificate cert) {
        // This would need to parse the CRL Distribution Points extension
        // For now, return empty list
        return new ArrayList<>();
    }

    // Add this to your AIAExtractor class if not already present
    public static List<String> getOCSPUrls(X509Certificate certificate) {
        List<String> ocspUrls = new ArrayList<>();

        try {
            byte[] aiaExtensionValue = certificate.getExtensionValue(Extension.authorityInfoAccess.getId());
            if (aiaExtensionValue == null) {
                return ocspUrls;
            }

            ASN1OctetString octetString = ASN1OctetString.getInstance(aiaExtensionValue);
            ASN1Primitive aiaObj = ASN1Primitive.fromByteArray(octetString.getOctets());
            AuthorityInformationAccess aia = AuthorityInformationAccess.getInstance(aiaObj);

            if (aia != null) {
                AccessDescription[] accessDescriptions = aia.getAccessDescriptions();

                for (AccessDescription accessDesc : accessDescriptions) {
                    if (X509ObjectIdentifiers.id_ad_ocsp.equals(accessDesc.getAccessMethod())) {
                        GeneralName accessLocation = accessDesc.getAccessLocation();
                        if (accessLocation.getTagNo() == GeneralName.uniformResourceIdentifier) {
                            String url = accessLocation.getName().toString();
                            ocspUrls.add(url);
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error parsing AIA extension for OCSP: " + e.getMessage());
        }

        return ocspUrls;
    }

}