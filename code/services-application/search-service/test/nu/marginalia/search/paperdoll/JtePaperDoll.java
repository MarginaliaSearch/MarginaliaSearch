package nu.marginalia.search.paperdoll;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import io.jooby.ExecutionMode;
import io.jooby.Jooby;
import io.jooby.MediaType;
import nu.marginalia.WebsiteUrl;
import nu.marginalia.functions.searchquery.searchfilter.model.SearchFilterSpec;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.search.model.NavbarModel;
import nu.marginalia.search.rendering.MockedSearchResults;
import nu.marginalia.search.svc.SearchFrontPageService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Tag("paperdoll")
public class JtePaperDoll {
    final CodeResolver codeResolver = new DirectoryCodeResolver(Path.of(".").toAbsolutePath().resolve("resources/jte"));
    final TemplateEngine templateEngine = TemplateEngine.create(codeResolver, ContentType.Html);
    LanguageConfiguration languageConfiguration;

    private String render(String template, Object obj) {
        var str = new StringOutput();
        templateEngine.render(template, obj, str);
        return str.toString();
    }

    private String render(String template, Map<String, Object> map) {
        var str = new StringOutput();
        templateEngine.render(template, map, str);
        return str.toString();
    }

    @Test
    public void searchResults() throws Exception {
        if (!Boolean.getBoolean("runPaperDoll")) {
            return;
        }
        languageConfiguration = new LanguageConfiguration();

        System.setProperty("test-env", "true");
        System.out.println(Path.of(".").toAbsolutePath());

        Thread serverThread = new Thread(() ->
            Jooby.runApp(new String[]{"application.env=prod"}, ExecutionMode.WORKER, () -> new Jooby() {{
                setServerOptions(new io.jooby.ServerOptions().setPort(9999));

                assets("/*", "/static");

                get("/suggest/", ctx -> {
                    ctx.setResponseType(MediaType.json);
                    return "[\"pla\",\"plaa\",\"plab\",\"plac\",\"pla2b\",\"pla2l\",\"plaaz\",\"plac1\",\"placa\",\"place\"]";
                });
                get("/", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("serp/main.jte", Map.of(
                            "results", MockedSearchResults.mockRegularSearchResults(),
                            "navbar", NavbarModel.SEARCH,
                            "languageDefinitions", languageConfiguration.languagesMap()));
                });
                get("/filter", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("filter/main.jte", Map.of(
                            "navbar", NavbarModel.SEARCH,
                            "languageDefinitions", languageConfiguration.languagesMap(),
                            "filter", SearchFilterSpec.defaultForUser("WEB", "ADHOC")));
                });
                get("/site-focus", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("serp/main.jte", Map.of(
                            "results", MockedSearchResults.mockSiteFocusResults(),
                            "navbar", NavbarModel.SEARCH));
                });
                get("/errors", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("serp/error.jte", Map.of(
                            "model", MockedSearchResults.mockErrorData(),
                            "navbar", NavbarModel.LIMBO,
                            "languageDefinitions", languageConfiguration.languagesMap()));
                });
                get("/first", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("serp/start.jte", Map.of(
                            "navbar", NavbarModel.SEARCH,
                            "model", new SearchFrontPageService.IndexModel(List.of(), "2024-01-01", 1),
                            "websiteUrl", new WebsiteUrl("https://localhost:9999/")));
                });
                get("/explore", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("explore/main.jte", Map.of(
                            "navbar", NavbarModel.EXPLORE,
                            "results", MockedSearchResults.mockBrowseResults(32)));
                });
                get("/site-info", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    String view = ctx.query("view").valueOrNull();
                    Object model;
                    if ("links".equals(view)) {
                        model = MockedSearchResults.mockBacklinkData();
                    } else if ("docs".equals(view)) {
                        model = MockedSearchResults.mockDocsData();
                    } else if ("report".equals(view)) {
                        model = MockedSearchResults.mockReportDomain();
                    } else if ("traffic".equals(view)) {
                        model = MockedSearchResults.mockTrafficReport();
                    } else if ("availability".equals(view)) {
                        model = MockedSearchResults.mockAvailabilityData();
                    } else if ("secevents".equals(view)) {
                        model = MockedSearchResults.mockSecurityEvents();
                    } else if ("secdetails".equals(view)) {
                        model = MockedSearchResults.mockSecurityDetails();
                    } else {
                        model = MockedSearchResults.mockSiteInfoData();
                    }
                    return render("siteinfo/main.jte", Map.of("model", model, "navbar", NavbarModel.SITEINFO));
                });
                get("/site-info-start", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("siteinfo/start.jte", Map.of(
                            "model", MockedSearchResults.mockSiteInfoOverview(),
                            "navbar", NavbarModel.SITEINFO));
                });
                get("/site-info-crosstalk-ab", ctx -> {
                    ctx.setResponseType(MediaType.html);
                    return render("siteinfo/crosstalk.jte", Map.of(
                            "model", MockedSearchResults.mockCrosstalkModel(),
                            "navbar", NavbarModel.SITEINFO));
                });
                get("/screenshot/*", ctx -> {
                    ctx.setResponseType("image/svg+xml");
                    return """
                            <svg viewBox="0 0 800 600" xmlns="http://www.w3.org/2000/svg">
                              <!-- Browser Window Frame -->
                              <rect x="0" y="0" width="800" height="600" rx="8" fill="#f1f5f9"/>
                              <rect x="0" y="0" width="800" height="40" rx="8" fill="#e2e8f0"/>
                              <!-- Browser Controls -->
                              <circle cx="20" cy="20" r="6" fill="#ef4444"/>
                              <circle cx="40" cy="20" r="6" fill="#fbbf24"/>
                              <circle cx="60" cy="20" r="6" fill="#22c55e"/>
                              <!-- Address Bar -->
                              <rect x="120" y="10" width="560" height="20" rx="4" fill="#ffffff"/>
                              <!-- Content Area -->
                              <rect x="20" y="60" width="760" height="80" rx="4" fill="#ffffff"/>
                              <rect x="40" y="80" width="400" height="16" rx="2" fill="#cbd5e1"/>
                              <rect x="40" y="104" width="300" height="16" rx="2" fill="#cbd5e1"/>
                              <!-- Navigation -->
                              <rect x="20" y="160" width="180" height="420" rx="4" fill="#ffffff"/>
                              <rect x="40" y="180" width="140" height="12" rx="2" fill="#cbd5e1"/>
                              <rect x="40" y="200" width="120" height="12" rx="2" fill="#cbd5e1"/>
                              <rect x="40" y="220" width="130" height="12" rx="2" fill="#cbd5e1"/>
                              <!-- Main Content -->
                              <rect x="220" y="160" width="560" height="420" rx="4" fill="#ffffff"/>
                              <rect x="240" y="180" width="520" height="180" rx="2" fill="#cbd5e1"/>
                              <rect x="240" y="380" width="520" height="12" rx="2" fill="#cbd5e1"/>
                              <rect x="240" y="400" width="480" height="12" rx="2" fill="#cbd5e1"/>
                              <rect x="240" y="420" width="500" height="12" rx="2" fill="#cbd5e1"/>
                              <rect x="240" y="440" width="460" height="12" rx="2" fill="#cbd5e1"/>
                            </svg>
                            """;
                });
            }}),
            "jooby-paperdoll"
        );
        serverThread.setDaemon(true);
        serverThread.start();

        System.out.println("PaperDoll server running at http://localhost:9999/");
        System.out.println("Press Ctrl+C to stop.");

        //noinspection InfiniteLoopStatement
        for (;;) Thread.sleep(60_000);
    }

}
