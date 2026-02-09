package nu.marginalia.ping.ssl;

import java.security.cert.TrustAnchor;
import java.time.Duration;
import java.util.Set;

public class RootCerts {
    private static final String MOZILLA_CA_BUNDLE_URL = "https://curl.se/ca/cacert.pem";

    volatile static boolean initialized = false;
    volatile static Set<TrustAnchor> trustAnchors;

    public static Set<TrustAnchor> getTrustAnchors() {
        if (!initialized) {
            try {
                synchronized (RootCerts.class) {
                    while (!initialized) {
                        RootCerts.class.wait(100);
                    }
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("RootCerts initialization interrupted", e);
            }
        }
        return trustAnchors;
    }

    static {
        Thread.ofPlatform()
                .name("RootCertsUpdater")
                .daemon()
                .start(RootCerts::updateTrustAnchors);
    }

    private static void updateTrustAnchors() {
        for (int attempts = 0; attempts < 5; attempts++) {
            try {
                trustAnchors = CertificateFetcher.getRootCerts(MOZILLA_CA_BUNDLE_URL);
                synchronized (RootCerts.class) {
                    initialized = true;
                    RootCerts.class.notifyAll(); // Notify any waiting threads
                }
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break; // Exit if interrupted
            } catch (Exception e) {
                // Log the exception and continue to retry
                System.err.println("Failed to update trust anchors: " + e.getMessage());
            }
        }

        throw new IllegalStateException("Failed to fetch trust anchors");
    }

}
