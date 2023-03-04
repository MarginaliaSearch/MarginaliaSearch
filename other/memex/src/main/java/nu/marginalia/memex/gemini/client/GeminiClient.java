package nu.marginalia.memex.gemini.client;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

/** Unstable code! */
public class GeminiClient {

    private final SSLSocketFactory socketFactory;

    // Create a trust manager that does not validate anything
    public static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain,
                                               String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain,
                                               String authType) {
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };


    public static SSLSocketFactory buildSocketFactory() throws Exception {
        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

        return sslContext.getSocketFactory();
    }

    public GeminiClient() throws Exception {
        socketFactory = buildSocketFactory();
    }

    public Response get(URI uri) throws IOException {

        final int port = uri.getPort() == -1 ? 1965 : uri.getPort();
        final String host = uri.getHost();
        var requestString = String.format("%s\r\n", uri).getBytes(StandardCharsets.UTF_8);

        try (var socket = socketFactory.createSocket(host, port)) {
            socket.setSoTimeout(10_000);
            socket.getOutputStream().write(requestString);

            var is = socket.getInputStream();
            String statusLine = new GeminiInput(is).get();

            int code = Integer.parseInt(statusLine.substring(0,2));
            String meta = statusLine.substring(3);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            is.transferTo(baos);

            return new Response(code, meta, baos.toByteArray());
        }

    }

    public static class Response {
        public final int code;
        public final String meta;
        public final byte[] data;

        Response(int code, String meta, byte[] data) {
            this.code = code;
            this.meta = meta;
            this.data = data;
        }
    }


    public static class GeminiInput {
        private final InputStream is;
        private final byte[] buffer = new byte[1024];
        private int idx;

        final String result;

        public GeminiInput(InputStream is) throws IOException {
            this.is = is;

            for (idx = 0; idx < buffer.length; idx++) {
                if (hasEndOfLine()) {
                    result = new String(buffer, 0, idx-2, StandardCharsets.UTF_8);
                    return;
                }

                readCharacter();
            }

            throw new RuntimeException("String too long");
        }

        public String get() {
            return result;
        }

        private void readCharacter() throws IOException {
            int rb = is.read();
            if (-1 == rb) {
                throw new RuntimeException("URL incomplete (no CR LF)");
            }
            buffer[idx] = (byte) rb;
        }

        public boolean hasEndOfLine() {
            return idx > 2
                    && buffer[idx - 1] == (byte) '\n'
                    && buffer[idx - 2] == (byte) '\r';
        }

    }
}
