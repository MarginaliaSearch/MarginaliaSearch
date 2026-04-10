package nu.marginalia.ping.ssl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.TrustAnchor;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class RootCerts {
    private static final Logger logger = LoggerFactory.getLogger(RootCerts.class);

    private static final String MOZILLA_CA_BUNDLE_URL = "https://curl.se/ca/cacert.pem";

    private static final CountDownLatch initialized = new CountDownLatch(1);
    private static volatile Set<TrustAnchor> trustAnchors;

    static {
        Thread.ofPlatform()
                .name("RootCertsUpdater")
                .daemon()
                .start(RootCerts::updateTrustAnchors);
    }

    public static Set<TrustAnchor> getTrustAnchors() {
        try {
            initialized.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RootCerts initialization interrupted", e);
        }
        return trustAnchors;
    }

    private static void updateTrustAnchors() {
        for (int attempts = 0; attempts < 5; attempts++) {
            try {
                trustAnchors = CertificateFetcher.getRootCerts(MOZILLA_CA_BUNDLE_URL);
                initialized.countDown();
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Failed to update trust anchors (attempt {}): {}", attempts + 1, e.getMessage());
            }
        }

        throw new IllegalStateException("Failed to fetch trust anchors");
    }
}
