package nu.marginalia.ping.ssl;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * Mock SSL session for hostname verification
 */
public class MockSSLSession implements SSLSession {
    private final X509Certificate[] peerCertificates;

    public MockSSLSession(X509Certificate cert) {
        this.peerCertificates = new X509Certificate[]{cert};
    }

    @Override
    public Certificate[] getPeerCertificates() throws SSLPeerUnverifiedException {
        return peerCertificates;
    }

    // All other methods return default/empty values as they're not used by hostname verification
    @Override
    public byte[] getId() {
        return new byte[0];
    }

    @Override
    public SSLSessionContext getSessionContext() {
        return null;
    }

    @Override
    public long getCreationTime() {
        return 0;
    }

    @Override
    public long getLastAccessedTime() {
        return 0;
    }

    @Override
    public void invalidate() {
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void putValue(String name, Object value) {
    }

    @Override
    public Object getValue(String name) {
        return null;
    }

    @Override
    public void removeValue(String name) {
    }

    @Override
    public String[] getValueNames() {
        return new String[0];
    }

    @Override
    public java.security.Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return null;
    }

    @Override
    public java.security.Principal getLocalPrincipal() {
        return null;
    }

    @Override
    public String getCipherSuite() {
        return "";
    }

    @Override
    public String getProtocol() {
        return "";
    }

    @Override
    public String getPeerHost() {
        return "";
    }

    @Override
    public int getPeerPort() {
        return 0;
    }

    @Override
    public int getPacketBufferSize() {
        return 0;
    }

    @Override
    public int getApplicationBufferSize() {
        return 0;
    }

    @Override
    public Certificate[] getLocalCertificates() {
        return new Certificate[0];
    }
}
