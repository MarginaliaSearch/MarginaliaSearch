package nu.marginalia.memex.gemini;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import nu.marginalia.memex.gemini.io.GeminiConnection;
import nu.marginalia.memex.gemini.io.GeminiSSLSetUp;
import nu.marginalia.memex.gemini.io.GeminiStatusCode;
import nu.marginalia.memex.gemini.io.GeminiUserException;
import nu.marginalia.memex.gemini.plugins.BareStaticPagePlugin;
import nu.marginalia.memex.gemini.plugins.Plugin;
import nu.marginalia.memex.gemini.plugins.SearchPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Singleton
public class GeminiServiceImpl implements GeminiService {

    public final Path serverRoot;

    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
    private final Executor pool = Executors.newFixedThreadPool(32);
    private final SSLServerSocket serverSocket;

    private final Plugin[] plugins;
    private final BadBotList badBotList = BadBotList.INSTANCE;

    @Inject
    public GeminiServiceImpl(@Named("gemini-server-root") Path serverRoot,
                             @Named("gemini-server-port") Integer port,
                             GeminiSSLSetUp sslSetUp,
                             BareStaticPagePlugin pagePlugin,
                             SearchPlugin searchPlugin) throws Exception {
        this.serverRoot = serverRoot;
        logger.info("Setting up crypto");
        final SSLServerSocketFactory socketFactory = sslSetUp.getServerSocketFactory();

        serverSocket = (SSLServerSocket) socketFactory.createServerSocket(port /* 1965 */);
        serverSocket.setEnabledCipherSuites(socketFactory.getSupportedCipherSuites());
        serverSocket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});

        logger.info("Verifying setup");
        if (!Files.exists(this.serverRoot)) {
            logger.error("Could not find SERVER_ROOT {}", this.serverRoot);
            System.exit(255);
        }

        plugins = new Plugin[] {
                pagePlugin,
                searchPlugin
        };
    }

    @Override
    public void run()  {
        logger.info("Awaiting connections");

        try {
            for (;;) {
                SSLSocket connection = (SSLSocket) serverSocket.accept();
                connection.setSoTimeout(10_000);

                if (!badBotList.isAllowed(connection.getInetAddress())) {
                    connection.close();
                } else {
                    pool.execute(() -> serve(connection));
                }
            }
        }
        catch (IOException ex) {
            logger.error("IO Exception in gemini server", ex);
        }
    }

    private void serve(SSLSocket socket) {
        final GeminiConnection connection;
        try {
            connection = new GeminiConnection(socket);
        }
        catch (IOException ex) {
            logger.error("Failed to create connection object", ex);
            return;
        }

        try {
            handleRequest(connection);
        }
        catch (GeminiUserException ex) {
            errorResponse(connection, ex.getMessage());
        }
        catch (SSLException ex) {
            logger.error(connection.getAddress() + " SSL error");
            connection.close();
        }
        catch (Exception ex) {
            errorResponse(connection, "Error");
            logger.error(connection.getAddress(), ex);
        }
        finally {
            connection.close();
        }
    }

    private void errorResponse(GeminiConnection connection, String message) {
        if (connection.isConnected()) {
            try {
                logger.error("=> " + connection.getAddress(), message);
                connection.writeStatusLine(GeminiStatusCode.ERROR_PERMANENT, message);
            }
            catch (IOException ex) {
                logger.error("Exception while sending error", ex);
            }
        }
    }

    private void handleRequest(GeminiConnection connection) throws Exception {

        final String address = connection.getAddress();
        logger.info("Connect: " + address);

        final Optional<URI> maybeUri = connection.readUrl();
        if (maybeUri.isEmpty()) {
            logger.info("Done: {}", address);
            return;
        }

        final URI uri = maybeUri.get();
        logger.info("Request {}", uri);

        if (!uri.getScheme().equals("gemini")) {
            throw new GeminiUserException("Unsupported protocol");
        }

        servePage(connection, uri);
        logger.info("Done: {}", address);
    }

    private void servePage(GeminiConnection connection, URI url) throws IOException {
        String path = url.getPath();

        for (Plugin p : plugins) {
            if (p.serve(url, connection)) {
                return;
            }
        }

        logger.error("FileNotFound {}", path);
        connection.writeStatusLine(GeminiStatusCode.ERROR_TEMPORARY, "No such file");
    }


}
