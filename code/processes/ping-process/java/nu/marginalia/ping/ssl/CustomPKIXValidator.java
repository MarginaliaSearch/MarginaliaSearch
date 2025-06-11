package nu.marginalia.ping.ssl;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.*;
import java.util.*;

/**
 * Custom PKIX validator for validating X.509 certificate chains with verbose output
 * for db export (i.e. not just SSLException).
 */
public class CustomPKIXValidator {

    private final Set<TrustAnchor> trustAnchors;
    private final boolean revocationEnabled;
    private final boolean anyPolicyInhibited;
    private final boolean explicitPolicyRequired;
    private final boolean policyMappingInhibited;
    private final Set<String> initialPolicies;

    private static final Set<String> EV_POLICY_OIDS = Set.of(
            "1.3.6.1.4.1.17326.10.14.2.1.2", // Entrust
            "1.3.6.1.4.1.17326.10.8.12.1.2",  // Entrust
            "2.16.840.1.114028.10.1.2",       // Entrust/AffirmTrust
            "1.3.6.1.4.1.6449.1.2.1.5.1",     // Comodo
            "1.3.6.1.4.1.8024.0.2.100.1.2",   // QuoVadis
            "2.16.840.1.114404.1.1.2.4.1",    // GoDaddy
            "2.16.840.1.114413.1.7.23.3",     // DigiCert
            "2.16.840.1.114414.1.7.23.3",     // DigiCert
            "1.3.6.1.4.1.14370.1.6",          // GlobalSign
            "2.16.756.1.89.1.2.1.1",          // SwissSign
            "1.3.6.1.4.1.4146.1.1"            // GlobalSign
    );

    // Constructor with default settings
    public CustomPKIXValidator() throws Exception {
        this(true, false, false, false, null);
    }

    // Constructor with custom settings
    public CustomPKIXValidator(boolean revocationEnabled,
                               boolean anyPolicyInhibited,
                               boolean explicitPolicyRequired,
                               boolean policyMappingInhibited,
                               Set<String> initialPolicies) throws Exception {
        this.trustAnchors = loadDefaultTrustAnchors();
        this.revocationEnabled = revocationEnabled;
        this.anyPolicyInhibited = anyPolicyInhibited;
        this.explicitPolicyRequired = explicitPolicyRequired;
        this.policyMappingInhibited = policyMappingInhibited;
        this.initialPolicies = initialPolicies;
    }

    // Constructor with custom trust anchors
    public CustomPKIXValidator(Set<TrustAnchor> customTrustAnchors,
                               boolean revocationEnabled) {
        this.trustAnchors = new HashSet<>(customTrustAnchors);
        this.revocationEnabled = revocationEnabled;
        this.anyPolicyInhibited = false;
        this.explicitPolicyRequired = false;
        this.policyMappingInhibited = false;
        this.initialPolicies = null;
    }

    /**
     * Validates certificate chain using PKIX algorithm
     */
    public PKIXValidationResult validateCertificateChain(String hostname, X509Certificate[] certChain) {
        EnumSet<PkixValidationError> errors = EnumSet.noneOf(PkixValidationError.class);
        try {
            // 1. Basic input validation
            if (certChain == null || certChain.length == 0) {
                return new PKIXValidationResult(false, "Certificate chain is empty", errors,
                        null, null, null, false);
            }

            if (hostname == null || hostname.trim().isEmpty()) {
                return new PKIXValidationResult(false, "Hostname is null or empty", errors,
                        null, null, null, false);
            }

            // 2. Create certificate path
            CertPath certPath = createCertificatePath(certChain);
            if (certPath == null) {
                return new PKIXValidationResult(false, "Failed to create certificate path", errors,
                        null, null, null, false);
            }

            // 3. Build and validate certificate path using PKIX
            PKIXCertPathValidatorResult pkixResult = performPKIXValidation(certPath, errors);

            // 4. Validate hostname
            boolean hostnameValid = validateHostname(hostname, certChain[0], errors);

            // 5. Extract critical extensions information
            Set<String> criticalExtensions = extractCriticalExtensions(certChain);

            boolean overallValid = (pkixResult != null) && hostnameValid;
            String errorMessage = null;

            if (pkixResult == null) {
                errorMessage = "PKIX path validation failed";
            } else if (!hostnameValid) {
                errorMessage = "Hostname validation failed";
            }

            return new PKIXValidationResult(overallValid, errorMessage, errors,
                    pkixResult, certPath, criticalExtensions, hostnameValid);

        } catch (Exception e) {
            return new PKIXValidationResult(false, "Validation exception: " + e.getMessage(),
                    errors, null, null, null, false);
        }
    }

    /**
     * Creates a certificate path from the certificate chain
     */
    private CertPath createCertificatePath(X509Certificate[] certChain) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List<Certificate> certList = Arrays.asList(certChain);
        return cf.generateCertPath(certList);
    }

    /**
     * Performs PKIX validation
     */
    private PKIXCertPathValidatorResult performPKIXValidation(CertPath certPath, Set<PkixValidationError> warnings) {
        try {
            // Create PKIX parameters
            PKIXParameters params = new PKIXParameters(trustAnchors);

            // Configure PKIX parameters
            params.setRevocationEnabled(revocationEnabled);
            params.setAnyPolicyInhibited(anyPolicyInhibited);
            params.setExplicitPolicyRequired(explicitPolicyRequired);
            params.setPolicyMappingInhibited(policyMappingInhibited);

            if (initialPolicies != null && !initialPolicies.isEmpty()) {
                params.setInitialPolicies(initialPolicies);
            }

            // Set up certificate stores for intermediate certificates if needed
            // This helps with path building when intermediate certs are missing
            List<Certificate> intermediateCerts = extractIntermediateCertificates(certPath);
            if (!intermediateCerts.isEmpty()) {
                CertStore certStore = CertStore.getInstance("Collection",
                        new CollectionCertStoreParameters(intermediateCerts));
                params.addCertStore(certStore);
            }

            // Configure revocation checking if enabled
            if (revocationEnabled) {
                configureRevocationChecking(params);
            }

            // Create and run validator
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult)
                    validator.validate(certPath, params);

            return result;

        } catch (CertPathValidatorException e) {
            warnings.add(PkixValidationError.PATH_VALIDATION_FAILED);
            return null;
        } catch (InvalidAlgorithmParameterException e) {
            warnings.add(PkixValidationError.INVALID_PKIX_PARAMETERS);
            return null;
        } catch (Exception e) {
            warnings.add(PkixValidationError.UNKNOWN);
            return null;
        }
    }

    /**
     * Extracts intermediate certificates from the path
     */
    private List<Certificate> extractIntermediateCertificates(CertPath certPath) {
        List<Certificate> certs = (List<Certificate>) certPath.getCertificates();
        if (certs.size() <= 2) {
            return new ArrayList<>(); // Only leaf and root, no intermediates
        }
        // Return all but the first (leaf) and potentially last (root)
        return new ArrayList<>(certs.subList(1, certs.size()));
    }

    /**
     * Configures revocation checking (CRL/OCSP)
     */
    private void configureRevocationChecking(PKIXParameters params) throws NoSuchAlgorithmException {
        // Create PKIX revocation checker
        PKIXRevocationChecker revocationChecker = (PKIXRevocationChecker)
                CertPathValidator.getInstance("PKIX").getRevocationChecker();

        // Configure revocation checker options
        Set<PKIXRevocationChecker.Option> options = EnumSet.of(
                PKIXRevocationChecker.Option.PREFER_CRLS,
                PKIXRevocationChecker.Option.SOFT_FAIL  // Don't fail if revocation info unavailable
        );
        revocationChecker.setOptions(options);

        params.addCertPathChecker(revocationChecker);
    }

    /**
     * Comprehensive hostname validation including SAN and CN
     */
    private boolean validateHostname(String hostname, X509Certificate cert, Set<PkixValidationError> warnings) {
        try {
            // Use Java's built-in hostname verifier as a starting point
            HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();

            // Create a mock SSL session for the hostname verifier
            MockSSLSession mockSession = new MockSSLSession(cert);

            boolean defaultResult = defaultVerifier.verify(hostname, mockSession);

            if (defaultResult) {
                return true;
            }

            // If default fails, do manual validation
            return performManualHostnameValidation(hostname, cert, warnings);

        } catch (Exception e) {
            warnings.add(PkixValidationError.UNSPECIFIED_HOST_ERROR);
            return false;
        }
    }

    /**
     * Manual hostname validation implementation
     */
    private boolean performManualHostnameValidation(String hostname, X509Certificate cert, Set<PkixValidationError> warnings) {
        try {
            // 1. Check Subject Alternative Names (SAN) - preferred method
            Collection<List<?>> sanEntries = cert.getSubjectAlternativeNames();
            if (sanEntries != null) {
                for (List<?> sanEntry : sanEntries) {
                    if (sanEntry.size() >= 2) {
                        Integer type = (Integer) sanEntry.get(0);
                        if (type == 2) { // DNS name
                            String dnsName = (String) sanEntry.get(1);
                            if (matchesHostname(hostname, dnsName)) {
                                return true;
                            }
                        } else if (type == 7) { // IP address
                            String ipAddress = (String) sanEntry.get(1);
                            if (hostname.equals(ipAddress)) {
                                return true;
                            }
                        }
                    }
                }
                // If SAN is present but no match found, don't check CN (RFC 6125)
                warnings.add(PkixValidationError.SAN_MISMATCH);
                return false;
            }

            // 2. Fallback to Common Name (CN) in subject if no SAN present
            String subjectDN = cert.getSubjectDN().getName();
            String cn = extractCommonName(subjectDN);
            if (cn != null) {
                if (matchesHostname(hostname, cn)) {
                    return true;
                }
            }

            warnings.add(PkixValidationError.SAN_MISMATCH);
            return false;

        } catch (Exception e) {
            warnings.add(PkixValidationError.UNKNOWN);
            return false;
        }
    }

    /**
     * Checks if hostname matches certificate name (handles wildcards)
     */
    private boolean matchesHostname(String hostname, String certName) {
        if (hostname == null || certName == null) {
            return false;
        }

        hostname = hostname.toLowerCase();
        certName = certName.toLowerCase();

        // Exact match
        if (hostname.equals(certName)) {
            return true;
        }

        // Wildcard matching (*.example.com)
        if (certName.startsWith("*.")) {
            String domain = certName.substring(2);

            // Wildcard must match exactly one level
            if (hostname.endsWith("." + domain)) {
                String prefix = hostname.substring(0, hostname.length() - domain.length() - 1);
                // Ensure wildcard doesn't match multiple levels (no dots in prefix)
                return !prefix.contains(".");
            }
        }

        return false;
    }

    /**
     * Extracts Common Name from Subject DN
     */
    private String extractCommonName(String subjectDN) {
        if (subjectDN == null) {
            return null;
        }

        // Parse DN components
        String[] components = subjectDN.split(",");
        for (String component : components) {
            component = component.trim();
            if (component.startsWith("CN=")) {
                return component.substring(3).trim();
            }
        }
        return null;
    }

    /**
     * Extracts critical extensions from all certificates in the chain
     */
    private Set<String> extractCriticalExtensions(X509Certificate[] certChain) {
        Set<String> allCriticalExtensions = new HashSet<>();

        for (X509Certificate cert : certChain) {
            Set<String> criticalExtensions = cert.getCriticalExtensionOIDs();
            if (criticalExtensions != null) {
                allCriticalExtensions.addAll(criticalExtensions);
            }
        }

        return allCriticalExtensions;
    }


    /**
     * Gets the key length from a certificate
     */
    private int getKeyLength(X509Certificate cert) {
        try {
            java.security.PublicKey publicKey = cert.getPublicKey();
            if (publicKey instanceof java.security.interfaces.RSAPublicKey) {
                return ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength();
            } else if (publicKey instanceof java.security.interfaces.DSAPublicKey) {
                return ((java.security.interfaces.DSAPublicKey) publicKey).getParams().getP().bitLength();
            } else if (publicKey instanceof java.security.interfaces.ECPublicKey) {
                return ((java.security.interfaces.ECPublicKey) publicKey).getParams().getOrder().bitLength();
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    /**
     * Checks if signature algorithm is considered weak
     */
    private boolean isWeakSignatureAlgorithm(String sigAlg) {
        if (sigAlg == null) return false;

        sigAlg = sigAlg.toLowerCase();
        return sigAlg.contains("md5") ||
                sigAlg.contains("sha1") ||
                sigAlg.equals("md2withrsa") ||
                sigAlg.equals("md4withrsa");
    }

    /**
     * Checks for deprecated or problematic extensions
     */
    private void checkDeprecatedExtensions(X509Certificate cert, int index, List<String> warnings) {
        // Check for Netscape extensions (deprecated)
        if (cert.getNonCriticalExtensionOIDs() != null) {
            for (String oid : cert.getNonCriticalExtensionOIDs()) {
                if (oid.startsWith("2.16.840.1.113730")) { // Netscape OID space
                    warnings.add("Certificate " + index + " contains deprecated Netscape extension: " + oid);
                }
            }
        }

        // Additional extension checks can be added here
    }

    /**
     * Loads default trust anchors from Java's cacerts keystore
     */
    private Set<TrustAnchor> loadDefaultTrustAnchors() throws Exception {
        Set<TrustAnchor> trustAnchors = new HashSet<>();

        // Try to load from default locations
        String[] keystorePaths = {
                System.getProperty("javax.net.ssl.trustStore"),
                System.getProperty("java.home") + "/lib/security/cacerts",
                System.getProperty("java.home") + "/jre/lib/security/cacerts"
        };

        String[] keystorePasswords = {
                System.getProperty("javax.net.ssl.trustStorePassword"),
                "changeit",
                ""
        };

        for (String keystorePath : keystorePaths) {
            if (keystorePath != null) {
                for (String password : keystorePasswords) {
                    try {
                        KeyStore trustStore = loadKeyStore(keystorePath, password);
                        if (trustStore != null) {
                            trustAnchors.addAll(extractTrustAnchors(trustStore));
                            if (!trustAnchors.isEmpty()) {
                                return trustAnchors;
                            }
                        }
                    } catch (Exception e) {
                        // Try next combination
                    }
                }
            }
        }

        // Fallback: try to get from default trust manager
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);

            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    X509TrustManager x509tm = (X509TrustManager) tm;
                    for (X509Certificate cert : x509tm.getAcceptedIssuers()) {
                        trustAnchors.add(new TrustAnchor(cert, null));
                    }
                }
            }
        } catch (Exception e) {
            throw new Exception("Failed to load any trust anchors", e);
        }

        if (trustAnchors.isEmpty()) {
            throw new Exception("No trust anchors could be loaded");
        }

        return trustAnchors;
    }

    /**
     * Loads a keystore from file
     */
    private KeyStore loadKeyStore(String keystorePath, String password) throws Exception {
        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keystore.load(fis, password != null ? password.toCharArray() : null);
            return keystore;
        }
    }

    /**
     * Extracts trust anchors from a keystore
     */
    private Set<TrustAnchor> extractTrustAnchors(KeyStore trustStore) throws KeyStoreException {
        Set<TrustAnchor> trustAnchors = new HashSet<>();

        Enumeration<String> aliases = trustStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (trustStore.isCertificateEntry(alias)) {
                Certificate cert = trustStore.getCertificate(alias);
                if (cert instanceof X509Certificate) {
                    trustAnchors.add(new TrustAnchor((X509Certificate) cert, null));
                }
            }
        }

        return trustAnchors;
    }

}
