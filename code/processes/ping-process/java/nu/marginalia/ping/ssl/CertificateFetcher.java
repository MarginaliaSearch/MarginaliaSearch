package nu.marginalia.ping.ssl;

import nu.marginalia.WmsaHome;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CertificateFetcher {

    private static final Logger logger = LoggerFactory.getLogger(CertificateFetcher.class);

    private static final HttpClient client = HttpClientBuilder.create().build();

    public static Set<TrustAnchor> getRootCerts(String bundleUrl) throws Exception {
        ClassicHttpRequest request = ClassicRequestBuilder.create("GET")
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

    private static List<X509Certificate> parseMultiplePEM(byte[] data) throws Exception {
        List<X509Certificate> certificates = new ArrayList<>();

        try (StringReader stringReader = new StringReader(new String(data, StandardCharsets.UTF_8));
             PEMParser pemParser = new PEMParser(stringReader)) {

            JcaX509CertificateConverter converter = new JcaX509CertificateConverter();
            Object object;

            while ((object = pemParser.readObject()) != null) {
                if (object instanceof X509CertificateHolder certHolder) {
                    certificates.add(converter.getCertificate(certHolder));
                } else if (object instanceof X509Certificate cert) {
                    certificates.add(cert);
                }
            }
        }

        return certificates;
    }
}