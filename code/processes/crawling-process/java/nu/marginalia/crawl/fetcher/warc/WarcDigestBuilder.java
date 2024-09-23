package nu.marginalia.crawl.fetcher.warc;

import org.netpreserve.jwarc.WarcDigest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class WarcDigestBuilder {
    private final MessageDigest digest;

    private static final String digestAlgorithm = "SHA-256";

    public WarcDigestBuilder() throws NoSuchAlgorithmException {
        this.digest = MessageDigest.getInstance(digestAlgorithm);
    }

    public void update(byte[] bytes) {
        update(bytes, bytes.length);
    }

    public void update(byte[] buffer, int n) {
        update(buffer, 0, n);
    }

    public void update(byte[] buffer, int s, int n) {
        digest.update(buffer, s, n);
    }

    public WarcDigest build() {
        return new WarcDigest(digest);
    }
}
