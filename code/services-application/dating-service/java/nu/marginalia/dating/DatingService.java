package nu.marginalia.dating;

import com.google.gson.Gson;
import com.google.inject.Inject;
import io.jooby.Context;
import io.jooby.Cookie;
import io.jooby.Jooby;
import io.jooby.SessionStore;
import nu.marginalia.browse.DbBrowseDomainsRandom;
import nu.marginalia.browse.DbBrowseDomainsSimilarCosine;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.db.DomainBlacklist;
import nu.marginalia.model.gson.GsonFactory;
import nu.marginalia.renderer.MustacheRenderer;
import nu.marginalia.renderer.RendererFactory;
import nu.marginalia.screenshot.ScreenshotService;
import nu.marginalia.service.server.BaseServiceParams;
import nu.marginalia.service.server.JoobyService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DatingService extends JoobyService {
    private final DomainBlacklist blacklist;
    private final DbBrowseDomainsSimilarCosine browseSimilarCosine;
    private final DbBrowseDomainsRandom browseRandom;
    private final MustacheRenderer<BrowseResult> datingRenderer;
    private final ScreenshotService screenshotService;
    private final String SESSION_OBJECT_NAME = "so";

    public final String WEBSITE_URL = System.getProperty("dating.website-url", "http://explore.marginalia.nu/");

    private static final Gson gson = GsonFactory.get();

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

    public void startJooby(Jooby jooby) {
        super.startJooby(jooby);

        jooby.setSessionStore(SessionStore.memory(Cookie.session("marginalia-session")));

        jooby.get("/reset", this::getReset);
        jooby.get("/", this::getInitSession);
        jooby.get("/init", this::getInitSession);
        jooby.get("/view", this::getCurrent);
        jooby.get("/next", this::getNext);
        jooby.get("/similar/{id}", this::getSimilar);
        jooby.get("/rewind", this::getRewind);
    }


    private String getReset(Context ctx) {
        var sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect(WEBSITE_URL);
            return "";
        }
        var session = sessionObjectOpt.get();
        session.resetQueue();
        saveSession(ctx, session);

        return getNext(ctx);
    }

    private String getCurrent(Context ctx) {
        var sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect(WEBSITE_URL);
            return "";
        }
        var session = sessionObjectOpt.get();

        var current = session.getCurrent();
        if (current == null) {
            BrowseResult res = session.next(browseRandom, blacklist);
            res = findViableDomain(session, res);
            session.browseForward(res);
            current = session.getCurrent();
            saveSession(ctx, session);
        }

        ctx.setResponseType("text/html");
        return datingRenderer.render(current, Map.of("back", session.hasHistory()));
    }

    private String getNext(Context ctx) {
        var sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect(WEBSITE_URL);
            return "";
        }
        var session = sessionObjectOpt.get();

        BrowseResult res = session.next(browseRandom, blacklist);

        res = findViableDomain(session, res);

        session.browseForward(res);
        saveSession(ctx, session);

        ctx.sendRedirect(WEBSITE_URL+"view");
        return "";
    }

    private String getRewind(Context ctx) {
        var sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect(WEBSITE_URL);
            return "";
        }
        var session = sessionObjectOpt.get();

        BrowseResult res = session.takeFromHistory();
        if (res == null) {
            ctx.setResponseCode(404);
            return "";
        }

        session.browseBackward(res);
        saveSession(ctx, session);

        ctx.sendRedirect(WEBSITE_URL+"view");
        return "";
    }


    private String getSimilar(Context ctx) {
        var sessionObjectOpt = getSession(ctx);
        if (sessionObjectOpt.isEmpty()) {
            ctx.sendRedirect(WEBSITE_URL);
            return "";
        }
        var session = sessionObjectOpt.get();

        int id = ctx.path("id").intValue();
        BrowseResult res = session.nextSimilar(id, browseSimilarCosine, blacklist);

        res = findViableDomain(session, res);

        session.browseForward(res);
        saveSession(ctx, session);

        ctx.sendRedirect(WEBSITE_URL + "view");
        return "";
    }

    @NotNull
    private BrowseResult findViableDomain(DatingSessionObject session, BrowseResult res) {
        while (!screenshotService.hasScreenshot(res.domainId()) || session.isRecent(res)) {
            res = session.next(browseRandom, blacklist);
        }
        return res;
    }

    private static final String EMPTY_SESSION = gson.toJson(new DatingSessionObject());

    private Object getInitSession(Context ctx) {
        var sess = ctx.session();

        if (sess.get(SESSION_OBJECT_NAME).isMissing()) {
            sess.put(SESSION_OBJECT_NAME, EMPTY_SESSION);
        }

        ctx.sendRedirect(WEBSITE_URL + "view");
        return "";
    }

    private Optional<DatingSessionObject> getSession(Context ctx) {
        var sess = ctx.sessionOrNull();

        if (sess == null)
            return Optional.empty();

        var encoded = sess.get(SESSION_OBJECT_NAME);
        if (encoded.isMissing())
            return Optional.empty();

        DatingSessionObject decodedSession = (DatingSessionObject) gson.fromJson(encoded.value(), DatingSessionObject.class);
        return Optional.of(decodedSession);
    }

    private void saveSession(Context ctx, DatingSessionObject session) {
        ctx.session().put(SESSION_OBJECT_NAME, gson.toJson(session));
    }
}
