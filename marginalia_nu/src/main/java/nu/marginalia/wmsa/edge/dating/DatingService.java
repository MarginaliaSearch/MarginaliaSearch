package nu.marginalia.wmsa.edge.dating;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.server.Initialization;
import nu.marginalia.wmsa.configuration.server.MetricsServer;
import nu.marginalia.wmsa.configuration.server.Service;
import nu.marginalia.wmsa.edge.assistant.screenshot.ScreenshotService;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDao;
import nu.marginalia.wmsa.edge.data.dao.task.EdgeDomainBlacklist;
import nu.marginalia.wmsa.edge.model.id.EdgeId;
import nu.marginalia.wmsa.edge.search.model.BrowseResult;
import nu.marginalia.wmsa.renderer.mustache.MustacheRenderer;
import nu.marginalia.wmsa.renderer.mustache.RendererFactory;
import org.jetbrains.annotations.NotNull;
import spark.Request;
import spark.Response;
import spark.Spark;
import spark.resource.ClassPathResource;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Optional;

public class DatingService extends Service {
    private final EdgeDataStoreDao edgeDataStoreDao;
    private final EdgeDomainBlacklist blacklist;
    private final MustacheRenderer<BrowseResult> datingRenderer;
    private final ScreenshotService screenshotService;
    private final String SESSION_OBJECT_NAME = "so";
    @SneakyThrows
    @Inject
    public DatingService(@Named("service-host") String ip,
                         @Named("service-port") Integer port,
                         EdgeDataStoreDao edgeDataStoreDao,
                         RendererFactory rendererFactory,
                         Initialization initialization,
                         MetricsServer metricsServer,
                         EdgeDomainBlacklist blacklist,
                         ScreenshotService screenshotService) {

        super(ip, port, initialization, metricsServer);

        this.edgeDataStoreDao = edgeDataStoreDao;
        this.blacklist = blacklist;

        datingRenderer = rendererFactory.renderer("dating/dating-view");
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
            BrowseResult res = session.next(edgeDataStoreDao, blacklist);
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

        BrowseResult res = session.next(edgeDataStoreDao, blacklist);

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
        BrowseResult res = session.nextSimilar(new EdgeId<>(id), edgeDataStoreDao, blacklist);

        res = findViableDomain(session, res);

        session.browseForward(res);

        response.redirect("https://explore.marginalia.nu/view");
        return "";
    }

    @NotNull
    private BrowseResult findViableDomain(DatingSessionObject session, BrowseResult res) {
        while (!screenshotService.hasScreenshot(new EdgeId<>(res.domainId)) || session.isRecent(res)) {
            res = session.next(edgeDataStoreDao, blacklist);
        }
        return res;
    }


    private Optional<DatingSessionObject> getSession(Request request) {
        return Optional.ofNullable(request.session(false))
                .map(s -> s.attribute(SESSION_OBJECT_NAME))
                .map(DatingSessionObject.class::cast);
    }
}
