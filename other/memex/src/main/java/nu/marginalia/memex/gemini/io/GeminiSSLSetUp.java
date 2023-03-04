package nu.marginalia.memex.gemini.io;

import com.google.inject.Inject;
import com.google.inject.name.Named;

import javax.net.ssl.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;

public class GeminiSSLSetUp {
    private final Path certPasswordFile;
    private final Path certFile;

    @Inject
    public GeminiSSLSetUp(
            @Named("gemini-cert-file") Path certFile,
            @Named("gemini-cert-password-file") Path certPasswordFile) {
        this.certFile = certFile;
        this.certPasswordFile = certPasswordFile;
    }
    public String getCertPassword() throws IOException {
        return Files.readString(certPasswordFile);
    }

    private SSLContext getContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS", "SUN");
        ks.load(Files.newInputStream(certFile), getCertPassword().toCharArray());

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, getCertPassword().toCharArray());
        KeyManager[] keyManagers = kmf.getKeyManagers();

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
        tmf.init(ks);
        TrustManager[] trustManagers = tmf.getTrustManagers();

        var ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(keyManagers, trustManagers, new SecureRandom());
        return ctx;
    }


    public SSLServerSocketFactory getServerSocketFactory() throws Exception {
        return getContext().getServerSocketFactory();
    }
}
