package nu.marginalia.dating;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Jooby;
import io.jooby.StatusCode;
import io.jooby.exception.StatusCodeException;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.DbBrowseDomainsSimilarCosine;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class DatingService extends JoobyService {
    private final DomainBlacklist blacklist;
    private final DbBrowseDomainsSimilarCosine browseSimilarCosine;
    private final DbBrowseDomainsRandom browseRandom;
    private final MustacheRenderer<BrowseResult> datingRenderer;
    private final ScreenshotService screenshotService;

    private static final String SESSION_ID_KEY = "dating-session-id";

    private final Cache<String, DatingSessionObject> sessionMap = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    @Inject
    public DatingService(BaseServiceParams params,
                         RendererFactory rendererFactory,
                         DomainBlacklist blacklist,
                         DbBrowseDomainsSimilarCosine browseSimilarCosine,
                         DbBrowseDomainsRandom browseRandom,
                         ScreenshotService screenshotService)
            throws Exception
    {
        super(params, List.of(), List.of());

        this.blacklist = blacklist;

        datingRenderer = rendererFactory.renderer("dating/dating-view");
        this.browseSimilarCosine = browseSimilarCosine;
        this.browseRandom = browseRandom;
        this.screenshotService = screenshotService;
    }

    @Override
    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        jooby.get("/reset", this::getReset);
        jooby.get("/", this::getInitSession);
        jooby.get("/view", this::getCurrent);
        jooby.get("/next", this::getNext);
        jooby.get("/similar/{id}", this::getSimilar);
        jooby.get("/rewind", this::getRewind);
        jooby.get("/init", this::getInitSession);
    }

    private Object getInitSession(Context ctx) {
        Optional<DatingSessionObject> sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            String id = UUID.randomUUID().toString();
            ctx.session().put(SESSION_ID_KEY, id);
            sessionMap.put(id, new DatingSessionObject());
        }
        ctx.sendRedirect("https://explore.marginalia.nu/view");
        return "";
    }

    private String getReset(Context ctx) {
        Optional<DatingSessionObject> sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect("https://explore.marginalia.nu/");
            return "";
        }
        DatingSessionObject session = sessionObjectOpt.get();
        session.resetQueue();

        return getNext(ctx);
    }

    private String getCurrent(Context ctx) {
        Optional<DatingSessionObject> sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect("https://explore.marginalia.nu/");
            return "";
        }
        DatingSessionObject session = sessionObjectOpt.get();

        BrowseResult current = session.getCurrent();
        if (current == null) {
            BrowseResult res = session.next(browseRandom, blacklist);
            res = findViableDomain(session, res);
            session.browseForward(res);
            current = session.getCurrent();
        }

        return datingRenderer.render(current, Map.of("back", session.hasHistory()));
    }

    private String getNext(Context ctx) {
        Optional<DatingSessionObject> sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect("https://explore.marginalia.nu/");
            return "";
        }
        DatingSessionObject session = sessionObjectOpt.get();

        BrowseResult res = session.next(browseRandom, blacklist);

        res = findViableDomain(session, res);

        session.browseForward(res);

        ctx.sendRedirect("https://explore.marginalia.nu/view");
        return "";
    }

    private String getRewind(Context ctx) {
        Optional<DatingSessionObject> sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect("https://explore.marginalia.nu/");
            return "";
        }
        DatingSessionObject session = sessionObjectOpt.get();

        BrowseResult res = session.takeFromHistory();
        if (res == null) {
            throw new StatusCodeException(StatusCode.NOT_FOUND);
        }

        session.browseBackward(res);

        ctx.sendRedirect("https://explore.marginalia.nu/view");
        return "";
    }


    private String getSimilar(Context ctx) {
        Optional<DatingSessionObject> sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect("https://explore.marginalia.nu/");
            return "";
        }
        DatingSessionObject session = sessionObjectOpt.get();

        int id = Integer.parseInt(ctx.path("id").value());
        BrowseResult res = session.nextSimilar(id, browseSimilarCosine, blacklist);

        res = findViableDomain(session, res);

        session.browseForward(res);

        ctx.sendRedirect("https://explore.marginalia.nu/view");
        return "";
    }

    @NotNull
    private BrowseResult findViableDomain(DatingSessionObject session, BrowseResult res) {
        while (!screenshotService.hasScreenshot(res.domainId()) || session.isRecent(res)) {
            res = session.next(browseRandom, blacklist);
        }
        return res;
    }


    private Optional<DatingSessionObject> getSession(Context ctx) {
        String sessionId = ctx.sessionOrNull() != null
                ? ctx.session().get(SESSION_ID_KEY).valueOrNull()
                : null;
        if (sessionId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionMap.getIfPresent(sessionId));
    }
}
