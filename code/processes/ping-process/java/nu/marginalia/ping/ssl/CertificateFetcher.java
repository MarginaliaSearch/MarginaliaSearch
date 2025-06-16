package nu.marginalia.ping.ssl;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import nu.marginalia.WmsaHome;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CertificateFetcher {

    private static final Logger logger = LoggerFactory.getLogger(CertificateFetcher.class);

    private static HttpClient client = HttpClientBuilder.create()
            .build();

    private static Cache<String, X509Certificate> cache = CacheBuilder
            .newBuilder()
            .expireAfterAccess(Duration.ofHours(6))
            .maximumSize(10_000)
            .build();


    public static List<X509Certificate> fetchMissingIntermediates(X509Certificate leafCert) {
        List<X509Certificate> intermediates = new ArrayList<>();

        // Get CA Issuer URLs from AIA extension
        List<String> caIssuerUrls = AIAExtractor.getCaIssuerUrls(leafCert);

        for (String url : caIssuerUrls) {
            try {
                // Check cache first
                X509Certificate cached = cache.getIfPresent(url);
                if (cached != null) {
                    intermediates.add(cached);
                    continue;
                }

                // Download certificate
                X509Certificate downloaded = downloadCertificate(url);
                if (downloaded != null) {
                    // Verify this certificate can actually sign the leaf
                    if (canSign(downloaded, leafCert)) {
                        intermediates.add(downloaded);
                        cache.put(url, downloaded);
                        logger.info("Downloaded certificate for url: {}", url);
                    } else {
                        logger.warn("Downloaded certificate cannot sign leaf cert from: {}", url);
                    }
                }

            } catch (Exception e) {
                logger.error("Failed to fetch certificate from {}: {}", url, e.getMessage());
            }
        }

        return intermediates;
    }
    private static X509Certificate downloadCertificate(String urlString) {
        try {
            ClassicHttpRequest request =  ClassicRequestBuilder.create("GET")
                    .addHeader("User-Agent", WmsaHome.getUserAgent() + " (Certificate Fetcher)")
                    .setUri(urlString)
                    .build();

            byte[] data = client.execute(request, rsp -> {
                var entity = rsp.getEntity();
                if (entity == null) {
                    logger.warn("GET request returned no content for {}", urlString);
                    return null;
                }
                return entity.getContent().readAllBytes();
            });

            if (data.length == 0) {
                logger.warn("Empty response from {}", urlString);
                return null;
            }

            // Try different formats based on file extension
            if (urlString.toLowerCase().endsWith(".p7c") || urlString.toLowerCase().endsWith(".p7b")) {
                return parsePKCS7(data);
            } else {
                return parseX509(data);
            }

        } catch (Exception e) {
            logger.warn("Failed to fetch certificate from {}: {}", urlString, e.getMessage());
            return null;
        }
    }

    private static List<X509Certificate> parseMultiplePEM(byte[] data) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();

        try (StringReader stringReader = new StringReader(new String(data, StandardCharsets.UTF_8));
             PEMParser pemParser = new PEMParser(stringReader)) {

            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            Object object;

            while ((object = pemParser.readObject()) != null) {
                if (object instanceof X509CertificateHolder) {
                    X509CertificateHolder certHolder = (X509CertificateHolder) object;
                    certificates.add(converter.getCertificate(certHolder));
                } else if (object instanceof X509Certificate) {
                    certificates.add((X509Certificate) object);
                }
            }
        }

        return certificates;
    }
    private static X509Certificate parseX509(byte[] data) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(data));
    }

    private static X509Certificate parsePKCS7(byte[] data) throws Exception {
        try {
            // Parse PKCS#7/CMS structure
            CMSSignedData cmsData = new CMSSignedData(data);
            Store<X509CertificateHolder> certStore = cmsData.getCertificates();

            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();

            // Get the first certificate from the store
            for (X509CertificateHolder certHolder : certStore.getMatches(null)) {
                X509Certificate cert = converter.getCertificate(certHolder);
                return cert;
            }

            logger.warn("No certificates found in PKCS#7 structure");
            return null;

        } catch (Exception e) {
            logger.error("Failed to parse PKCS#7 structure from {}: {}", data.length, e.getMessage());
            return parseX509(data);
        }
    }

    private static boolean canSign(X509Certificate issuerCert, X509Certificate subjectCert) {
        try {
            // Check if the issuer DN matches
            if (!issuerCert.getSubjectDN().equals(subjectCert.getIssuerDN())) {
                return false;
            }

            // Try to verify the signature
            subjectCert.verify(issuerCert.getPublicKey());
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    // Recursive fetching for complete chains
    public static List<X509Certificate> buildCompleteChain(X509Certificate leafCert) {
        List<X509Certificate> completeChain = new ArrayList<>();
        completeChain.add(leafCert);

        X509Certificate currentCert = leafCert;
        int maxDepth = 10; // Prevent infinite loops

        while (maxDepth-- > 0) {
            // If current cert is self-signed (root), we're done
            if (currentCert.getSubjectDN().equals(currentCert.getIssuerDN())) {
                break;
            }

            // Try to find the issuer
            List<X509Certificate> intermediates = fetchMissingIntermediates(currentCert);
            if (intermediates.isEmpty()) {
                logger.error("Could not find issuer for: {}", currentCert.getSubjectDN());
                break;
            }

            // Add the first valid intermediate
            X509Certificate intermediate = intermediates.get(0);
            completeChain.add(intermediate);
            currentCert = intermediate;
        }

        return completeChain;
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
            logger.error("Error parsing AIA extension for OCSP: {}", e.getMessage());
        }

        return ocspUrls;
    }

    public static Set<TrustAnchor> getRootCerts(String bundleUrl) throws Exception {
        ClassicHttpRequest request =  ClassicRequestBuilder.create("GET")
                .addHeader("User-Agent", WmsaHome.getUserAgent() + " (Certificate Fetcher)")
                .setUri(bundleUrl)
                .build();

        byte[] data = client.execute(request, rsp -> {
            var entity = rsp.getEntity();
            if (entity == null) {
                logger.warn("GET request returned no content for {}", bundleUrl);
                return null;
            }
            return entity.getContent().readAllBytes();
        });

        List<TrustAnchor> anchors = new ArrayList<>();
        for (var cert : parseMultiplePEM(data)) {
            try {
                anchors.add(new TrustAnchor(cert, null));
            } catch (Exception e) {
                logger.warn("Failed to create TrustAnchor for certificate: {}", e.getMessage());
            }
        }

        logger.info("Loaded {} root certificates from {}", anchors.size(), bundleUrl);

        return Set.copyOf(anchors);
    }
}