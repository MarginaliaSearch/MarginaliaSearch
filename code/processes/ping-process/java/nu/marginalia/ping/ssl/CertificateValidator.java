package nu.marginalia.ping.ssl;

import javax.security.auth.x500.X500Principal;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Utility class for validating X.509 certificates.
 * <p>
 * This is extremely unsuitable for actual SSL/TLS validation,
 * and is only to be used in analyzing certificates for fingerprinting
 * and diagnosing servers!
 */
public class CertificateValidator {

    public static class ValidationResult {
        public boolean chainValid = false;
        public boolean certificateExpired = false;
        public boolean selfSigned = false;
        public boolean hostnameValid = false;

        public boolean isValid() {
            return !selfSigned && !certificateExpired && hostnameValid;
        }
    }

    public static ValidationResult validateCertificate(X509Certificate[] certChain,
                                                       String hostname) {
        ValidationResult result = new ValidationResult();

        if (certChain == null || certChain.length == 0) {
            return result;
        }

        X509Certificate leafCert = certChain[0];

        result.certificateExpired = isExpired(leafCert);
        result.hostnameValid = matchesCertificate(leafCert, hostname);
        result.selfSigned = leafCert.getSubjectX500Principal().equals(leafCert.getIssuerX500Principal());
        result.chainValid = isChainValid(certChain, RootCerts.getTrustAnchors());

        return result;
    }

    private static boolean isExpired(X509Certificate cert) {
        try {
            cert.checkValidity();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean matchesCertificate(X509Certificate cert, String hostname) {
        if (hostname == null || hostname.isEmpty()) {
            return false;
        }

        try {
            // Check Subject CN
            String subjectCN = getCommonName(cert.getSubjectX500Principal());
            if (subjectCN != null && matchesHostname(subjectCN, hostname)) {
                return true;
            }

            // Check Subject Alternative Names
            Collection<List<?>> subjectAltNames = cert.getSubjectAlternativeNames();
            if (subjectAltNames != null) {
                for (List<?> altName : subjectAltNames) {
                    if (altName.size() >= 2 && (Integer) altName.get(0) == 2) { // DNS name
                        if (matchesHostname((String) altName.get(1), hostname)) {
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isChainValid(X509Certificate[] chain, Set<TrustAnchor> trustAnchors) {
        for (int i = 0; i < chain.length; i++) {
            try {
                chain[i].checkValidity();
            } catch (Exception e) {
                return false;
            }

            if (i < chain.length - 1) {
                try {
                    chain[i].verify(chain[i + 1].getPublicKey());
                } catch (Exception e) {
                    return false;
                }

                if (!chain[i].getIssuerX500Principal().equals(chain[i + 1].getSubjectX500Principal())) {
                    return false;
                }
            }
        }

        return isTrustedRoot(chain[chain.length - 1], trustAnchors);
    }

    private static boolean isTrustedRoot(X509Certificate rootCert, Set<TrustAnchor> trustAnchors) {
        if (rootCert.getSubjectX500Principal().equals(rootCert.getIssuerX500Principal())) {
            // Self-signed - check if this cert or its subject is in trust store
            for (TrustAnchor anchor : trustAnchors) {
                if (anchor.getTrustedCert().equals(rootCert)
                        || anchor.getTrustedCert().getSubjectX500Principal().equals(rootCert.getSubjectX500Principal())) {
                    return true;
                }
            }
        } else {
            // Not self-signed - check if issuer is trusted
            for (TrustAnchor anchor : trustAnchors) {
                if (anchor.getTrustedCert().getSubjectX500Principal().equals(rootCert.getIssuerX500Principal())) {
                    return true;
                }
            }
        }

        return false;
    }

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
}
