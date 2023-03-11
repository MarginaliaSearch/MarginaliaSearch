package nu.marginalia.dating;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.DbBrowseDomainsSimilarCosine;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.model.dbcommon.DomainBlacklist;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.model.id.EdgeId;
import nu.marginalia.service.server.Initialization;
import nu.marginalia.service.server.MetricsServer;
import nu.marginalia.service.server.Service;
import org.jetbrains.annotations.NotNull;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.resource.ClassPathResource;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Optional;

public class DatingService extends Service {
    private final DomainBlacklist blacklist;
    private final DbBrowseDomainsSimilarCosine browseSimilarCosine;
    private final DbBrowseDomainsRandom browseRandom;
    private final MustacheRenderer<BrowseResult> datingRenderer;
    private final ScreenshotService screenshotService;
    private final String SESSION_OBJECT_NAME = "so";
    @SneakyThrows
    @Inject
    public DatingService(@Named("service-host") String ip,
                         @Named("service-port") Integer port,
                         RendererFactory rendererFactory,
                         Initialization initialization,
                         MetricsServer metricsServer,
                         DomainBlacklist blacklist,
                         DbBrowseDomainsSimilarCosine browseSimilarCosine,
                         DbBrowseDomainsRandom browseRandom,
                         ScreenshotService screenshotService) {

        super(ip, port, initialization, metricsServer);

        this.blacklist = blacklist;

        datingRenderer = rendererFactory.renderer("dating/dating-view");
        this.browseSimilarCosine = browseSimilarCosine;
        this.browseRandom = browseRandom;
        this.screenshotService = screenshotService;

        Spark.get("/public/reset", this::getReset);
        Spark.get("/public/", this::serveIndex);
        Spark.get("/public/view", this::getCurrent);
        Spark.get("/public/next", this::getNext);
        Spark.get("/public/similar/:id", this::getSimilar);
        Spark.get("/public/rewind", this::getRewind);
        Spark.get("/public/init", this::getInitSession);
    }

    @SneakyThrows
    private Object serveIndex(Request request, Response response) {
        try {
            ClassPathResource resource = new ClassPathResource("static/dating/index.html");
            resource.getInputStream().transferTo(response.raw().getOutputStream());
        }
        catch (IllegalArgumentException| FileNotFoundException ex) {
            return false;
        }
        return "";
    }

    private Object getInitSession(Request request, Response response) {
        var sessionObjectOpt = getSession(request);
        if (sessionObjectOpt.isEmpty()) {
            request.session(true).attribute(SESSION_OBJECT_NAME, new DatingSessionObject());
        }
        response.redirect("https://explore.marginalia.nu/view");
        return "";
    }

    private String getReset(Request request, Response response) {
        var sessionObjectOpt = getSession(request);
        if (sessionObjectOpt.isEmpty()) {
            response.redirect("https://explore.marginalia.nu/");
            return "";
        }
        var session = sessionObjectOpt.get();
        session.resetQueue();

        return getNext(request, response);
    }

    private String getCurrent(Request request, Response response) {
        var sessionObjectOpt = getSession(request);
        if (sessionObjectOpt.isEmpty()) {
            response.redirect("https://explore.marginalia.nu/");
            return "";
        }
        var session = sessionObjectOpt.get();

        var current = session.getCurrent();
        if (current == null) {
            BrowseResult res = session.next(browseRandom, blacklist);
            res = findViableDomain(session, res);
            session.browseForward(res);
            current = session.getCurrent();
        }

        return datingRenderer.render(current, Map.of("back", session.hasHistory()));
    }

    private String getNext(Request request, Response response) {
        var sessionObjectOpt = getSession(request);
        if (sessionObjectOpt.isEmpty()) {
            response.redirect("https://explore.marginalia.nu/");
            return "";
        }
        var session = sessionObjectOpt.get();

        BrowseResult res = session.next(browseRandom, blacklist);

        res = findViableDomain(session, res);

        session.browseForward(res);

        response.redirect("https://explore.marginalia.nu/view");
        return "";
    }

    private String getRewind(Request request, Response response) {
        var sessionObjectOpt = getSession(request);
        if (sessionObjectOpt.isEmpty()) {
            response.redirect("https://explore.marginalia.nu/");
            return "";
        }
        var session = sessionObjectOpt.get();

        BrowseResult res = session.takeFromHistory();
        if (res == null) {
            Spark.halt(404);
            return "";
        }

        session.browseBackward(res);

        response.redirect("https://explore.marginalia.nu/view");
        return "";
    }


    private String getSimilar(Request request, Response response) {
        var sessionObjectOpt = getSession(request);
        if (sessionObjectOpt.isEmpty()) {
            response.redirect("https://explore.marginalia.nu/");
            return "";
        }
        var session = sessionObjectOpt.get();

        int id = Integer.parseInt(request.params("id"));
        BrowseResult res = session.nextSimilar(new EdgeId<>(id), browseSimilarCosine, blacklist);

        res = findViableDomain(session, res);

        session.browseForward(res);

        response.redirect("https://explore.marginalia.nu/view");
        return "";
    }

    @NotNull
    private BrowseResult findViableDomain(DatingSessionObject session, BrowseResult res) {
        while (!screenshotService.hasScreenshot(new EdgeId<>(res.domainId())) || session.isRecent(res)) {
            res = session.next(browseRandom, blacklist);
        }
        return res;
    }


    private Optional<DatingSessionObject> getSession(Request request) {
        return Optional.ofNullable(request.session(false))
                .map(s -> s.attribute(SESSION_OBJECT_NAME))
                .map(DatingSessionObject.class::cast);
    }
}
