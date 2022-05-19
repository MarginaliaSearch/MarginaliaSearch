package nu.marginalia.wmsa.auth;

import com.github.jknack.handlebars.internal.Files;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.wmsa.auth.model.LoginFormModel;
import nu.marginalia.wmsa.configuration.server.*;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import static spark.Spark.*;

public class AuthService extends Service {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private String password;

    private final RateLimiter rateLimiter =  RateLimiter.forLogin();
    private final MustacheRenderer<LoginFormModel> loginFormRenderer;

    @Inject
    public AuthService(@Named("service-host") String ip,
                       @Named("service-port") Integer port,
                       @Named("password-file") Path topSecretPasswordFile,
                       RendererFactory rendererFactory,
                       Initialization initialization,
                       MetricsServer metricsServer) throws IOException {

        super(ip, port, initialization, metricsServer);

        try (var is = new FileReader(topSecretPasswordFile.toFile())) {
            password = Files.read(is);
        } catch (IOException e) {
            logger.error("Could not read password from file " + topSecretPasswordFile, e);
        }
        loginFormRenderer = rendererFactory.renderer("auth/login");

        Spark.path("public/api", () -> {
            before((req, rsp) -> {
                logger.info("{} {}", req.requestMethod(), req.pathInfo());
            });

            post("/login", this::login);
            get("/login", this::loginForm);
        });
        Spark.path("api", () -> {
            get("/is-logged-in", this::isLoggedIn);
        });
    }

    private Object loginForm(Request request, Response response) throws IOException {
        String redir = Objects.requireNonNull(request.queryParams("redirect"));
        String service = Objects.requireNonNull(request.queryParams("service"));

        return loginFormRenderer.render(new LoginFormModel(service, redir));
    }

    private Object login(Request request, Response response) {
        var redir = Objects.requireNonNullElse(request.queryParams("redirect"), "/");

        if (isLoggedIn(request, response)) {
            response.redirect(redir);
            return "";
        }

        if (!rateLimiter.isAllowed(Context.fromRequest(request))) {
            Spark.halt(429, "Too many requests");
            return null;
        }

        if (Objects.equals(password, request.queryParams("password"))) {
            request.session(true).attribute("logged-in", true);
            response.redirect(redir);
            return "";
        }

        response.status(HttpStatus.SC_FORBIDDEN);
        return "<h1>Bad password!</h1>";
    }

    public boolean isLoggedIn(Request request, Response response) {
        var session = request.session(false);

        if (null == session) {
            return false;
        }

        return Optional.ofNullable(session.attribute("logged-in"))
                .map(Boolean.class::cast)
                .orElse(false);
    }

}
