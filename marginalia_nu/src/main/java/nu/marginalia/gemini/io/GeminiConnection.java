package nu.marginalia.gemini.io;

import nu.marginalia.gemini.BadBotList;
import nu.marginalia.gemini.plugins.FileType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class GeminiConnection {
    private final SSLSocket connection;

    private final Logger logger = LoggerFactory.getLogger("Server");
    private final OutputStream os;
    private final InputStream is;
    private static final BadBotList badBotList = BadBotList.INSTANCE;

    public GeminiConnection(SSLSocket connection) throws IOException {
        this.connection = connection;

        this.os = connection.getOutputStream();
        this.is = connection.getInputStream();

    }

    public String getAddress() {
        return connection.getInetAddress().getHostAddress();
    }

    public Optional<URI> readUrl() throws Exception {

        var str = new GeminiInput().get();
        if (!badBotList.isQueryPermitted(connection.getInetAddress(), str)) {
            return Optional.empty();
        }
        if (!str.isBlank()) {
            return Optional.of(new URI(str));
        }
        throw new GeminiUserException("Bad URI");
    }

    public void redirect(String address) throws IOException {
        writeStatusLine(GeminiStatusCode.REDIRECT, address);
    }
    public void redirectPermanent(String address) throws IOException {
        writeStatusLine(GeminiStatusCode.REDIRECT_PERMANENT, address);
    }
    public GeminiConnection writeStatusLine(int code, String meta) throws IOException {
        write(String.format("%2d %s", code, meta));
        return this;
    }

    public GeminiConnection writeBytes(byte[] data) throws IOException {
        write(data);
        return this;
    }

    public GeminiConnection printf(String pattern, Object...args) throws IOException {
        write(String.format(pattern, args));
        return this;
    }

    public GeminiConnection writeLines(String... lines) throws IOException {
        for (String s : lines) {
            write(s);
        }
        return this;
    }
    public GeminiConnection writeLinesFromFile(Path file) throws IOException {
        try (Stream<String> lines = Files.lines(file)) {
            lines.forEach(line -> {
                try {
                    write(line);
                } catch (IOException e) {
                    logger.error("IO Error", e);
                }
            });
        }
        return this;
    }

    public GeminiConnection acceptLines(Stream<String> lines) {
        lines.forEach(line -> {
            try {
                write(line);
            } catch (IOException e) {
                logger.error("IO exception", e);
            }
        });
        return this;
    }

    private void write(String s) throws IOException {
        os.write(s.getBytes(StandardCharsets.UTF_8));
        os.write(new byte[] { '\r', '\n'});
    }

    private void write(byte[] bs) throws IOException {
        os.write(bs);
    }
    // This is a weird pattern but it makes the listing code very much cleaner

    public void error(String message) {
        logger.error("{}", message);

        throw new GeminiUserException(message);
    }

    public void close() {
        try {
            connection.shutdownOutput();
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public void respondWithFile(Path serverPath, FileType fileType) throws IOException {
        if (fileType.binary) {
            writeStatusLine(GeminiStatusCode.SUCCESS, fileType.mime)
                    .writeBytes(Files.readAllBytes(serverPath));
        }
        else {
            writeStatusLine(GeminiStatusCode.SUCCESS, fileType.mime)
                    .writeLinesFromFile(serverPath);
        }
    }

    public class GeminiInput {
        private final byte[] buffer = new byte[1024];
        private int idx = 0;

        final String result;

        public GeminiInput() throws IOException {

            for (idx = 0; idx < buffer.length; idx++) {
                if (hasEndOfLine()) {
                    result = new String(buffer, 0, idx-2, StandardCharsets.UTF_8);
                    return;
                }

                readCharacter();
            }

            error("String too long");

            // unreachable
            result = "";
        }

        public String get() {
            return result;
        }

        private void readCharacter() throws IOException {
            int rb = is.read();
            if (-1 == rb) {
                error("URL incomplete (no CR LF)");
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
