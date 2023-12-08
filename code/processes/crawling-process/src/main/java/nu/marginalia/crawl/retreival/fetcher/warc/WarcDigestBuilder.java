package nu.marginalia.crawl.retreival.fetcher.warc;

import org.netpreserve.jwarc.WarcDigest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class WarcDigestBuilder {
    private final MessageDigest digest;

    private static final String digestAlgorithm = "SHA-1";

    public WarcDigestBuilder() throws NoSuchAlgorithmException {
        this.digest = MessageDigest.getInstance(digestAlgorithm);
    }

    public void update(String s) {
        byte[] bytes = s.getBytes();
        update(bytes, bytes.length);
    }

    public void update(byte[] buffer, int n) {
        digest.update(buffer, 0, n);
    }

    public WarcDigest build() {
        return new WarcDigest(digest);
    }
}
