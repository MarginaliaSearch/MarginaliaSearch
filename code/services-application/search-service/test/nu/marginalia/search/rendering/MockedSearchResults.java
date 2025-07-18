package nu.marginalia.search.rendering;

import nu.marginalia.WebsiteUrl;
import nu.marginalia.api.domains.model.DomainInformation;
import nu.marginalia.api.domains.model.SimilarDomain;
import nu.marginalia.api.searchquery.model.results.SearchResultItem;
import nu.marginalia.browse.model.BrowseResult;
import nu.marginalia.browse.model.BrowseResultSet;
import nu.marginalia.db.DbDomainQueries;
import nu.marginalia.ddtrackergradar.model.DDGTDomain;
import nu.marginalia.ddtrackergradar.model.DDGTOwner;
import nu.marginalia.domclassifier.DomSampleClassifier;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.search.command.SearchParameters;
import nu.marginalia.search.model.*;
import nu.marginalia.search.svc.SearchCrosstalkService;
import nu.marginalia.search.svc.SearchFlagSiteService;
import nu.marginalia.search.svc.SearchSiteInfoService;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MockedSearchResults {

    private static UrlDetails mockUrlDetails(String url, String title) throws URISyntaxException {
        return mockUrlDetails(url, title, "Sing, Goddess, sing the rage of Achilles, son of Peleus—\n" +
                "that murderous anger which condemned Achaeans\n" +
                "to countless agonies and threw many warrior souls\n" +
                "deep into Hades, leaving their dead bodies\n" +
                "carrion food for dogs and birds—\n" +
                "all in fulfilment of the will of Zeus.");

    }

    private static UrlDetails mockUrlDetails(String url, String title, String desc) throws URISyntaxException {
        return new UrlDetails(
                1,
                1,
                new EdgeUrl(url),
                title,
                desc,
                "HTML5",
                ThreadLocalRandom.current().nextInt(),
                DomainIndexingState.ACTIVE,
                0.5,
                8,
                "",
                mockPositionsMask(),
                2,
                new SearchResultItem(0, 0, 0, 0, 0),
                null);

    }
    private static long mockPositionsMask() {

        int hits = ThreadLocalRandom.current().nextInt(1, 24);
        long mask = 0;
        for (int i = 0; i < hits; i++) {
            mask |= 1L << ThreadLocalRandom.current().nextInt(0, 64);
        }

        return mask;
    }

    private static List<ClusteredUrlDetails> mockSearchResultsList() throws URISyntaxException {
        return List.of(
                // Non-clustered result
                new ClusteredUrlDetails(
                        mockUrlDetails("https://clustered.marginalia.nu", "Non-clustered-result")
                ),
                new ClusteredUrlDetails(
                        mockUrlDetails("https://clustered.marginalia.nu", "Short Result", "Short")
                ),                new ClusteredUrlDetails(
                        mockUrlDetails("https://clustered.marginalia.nu", "Long Result", "Posted on Jun 14th 2015 at 12:00:00 PM by Posted under [img width=603 height=517]http www.techspot.com/images2/news/bigimage/2014-10-23-image-21.jpg[/img] Gamers who grew up with consoles have been lucky over the years as far as nostalgia goes. In the las")
                ),
                new ClusteredUrlDetails(
                        mockUrlDetails("https://clustered.marginalia.nu", "Clustered-result"),
                        List.of(
                                mockUrlDetails("https://clustered.marginalia.nu", "Additional result"),
                                mockUrlDetails("https://clustered.marginalia.nu", "One more result")
                        )
                )
        );
    }

    public static DecoratedSearchResults mockRegularSearchResults() throws URISyntaxException {
        SearchParameters params = SearchParameters.defaultsForQuery(new WebsiteUrl("https://localhost:9999/"), "test", 1);

        return new DecoratedSearchResults(
                params,
                List.of("Not enough search engine oil"),
                null,
                mockSearchResultsList(),
                "",
                -1,
                new SearchFilters(params),
                List.of(new ResultsPage(1, true, "#"),
                        new ResultsPage(2, false, "#")));
    }

    public static DecoratedSearchResults mockSiteFocusResults() throws URISyntaxException {
        SearchParameters params = SearchParameters.defaultsForQuery(new WebsiteUrl("https://localhost:9999/"), "test site:example.marginalia.nu", 1);

        return new DecoratedSearchResults(
                params,
                List.of("Not enough search engine oil"),
                null,
                mockSearchResultsList(),
                "example.marginalia.nu",
                1,
                new SearchFilters(params),
                List.of(new ResultsPage(1, true, "#"),
                        new ResultsPage(2, false, "#")));
    }

    public static SearchErrorMessageModel mockErrorData() {
        var params = SearchParameters.defaultsForQuery(new WebsiteUrl("https://localhost:9999/"), "test site:example.marginalia.nu", 1);

        return new SearchErrorMessageModel(
                "An error occurred when communicating with the search engine index.",
                """
                            This is hopefully a temporary state of affairs.  It may be due to
                            an upgrade.  The index typically takes a about two or three minutes
                            to reload from a cold restart.  Thanks for your patience.
                            """,
                params,
                new SearchFilters(params)
        );
    }

    public static SearchSiteInfoService.SiteInfoWithContext mockSiteInfoData() throws URISyntaxException {
        return new SearchSiteInfoService.SiteInfoWithContext(
                "www.example.com",
                false,
                List.of(
                        new DbDomainQueries.DomainWithNode(new EdgeDomain("example.com"), 1),
                        new DbDomainQueries.DomainWithNode(new EdgeDomain("example.com"), 0)
                        ),
                14,
                "https://www.example.com",
                true,
                new DomainInformation(
                        new EdgeDomain("www.example.com"),
                        false,
                        14,
                        23,
                        55,
                        10,
                        20,
                        1,
                        0.5,
                        false,
                        true,
                        false,
                        "127.0.0.1",
                        4041,
                        "ACME INC",
                        "SE",
                        "SE",
                        "INDEXED"
                ),
                List.of(
                        new SimilarDomain(new EdgeUrl("https://www.other.com"), 4,65, 20, true, true, true, true, SimilarDomain.LinkType.BIDIRECTIONAL)
                ),
                List.of(
                        new SimilarDomain(new EdgeUrl("https://www.other.com"), 4,65, 80, true, true, true, false, SimilarDomain.LinkType.BIDIRECTIONAL),
                        new SimilarDomain(new EdgeUrl("https://www.other.com"), 4,35, 40, true, true, false, false, SimilarDomain.LinkType.BACKWARD),
                        new SimilarDomain(new EdgeUrl("https://www.other.com"), 4,25, 20, true, true, false, false, SimilarDomain.LinkType.FOWARD),
                        new SimilarDomain(new EdgeUrl("https://www.other.com"), 4,25, 20, true, true, false, false, SimilarDomain.LinkType.FOWARD)
                ),
                new SearchSiteInfoService.FeedItems("www.example.com",
                        "https://www.example.com/rss.xml",
                        "2024-01-01",
                        List.of(
                                new SearchSiteInfoService.FeedItem("Test Post", "2024-01-01", "Lorem ipsum dolor sit amet", "https://www.example.com/1"),
                                new SearchSiteInfoService.FeedItem("Other Post", "2024-01-04", "Sing, Goddess, sing the rage of Achilles, son of Peleus—\n" +
                                        "that murderous anger which condemned Achaeans\n" +
                                        "to countless agonies and threw many warrior souls\n" +
                                        "deep into Hades, leaving their dead bodies\n" +
                                        "carrion food for dogs and birds—\n" +
                                        "all in fulfilment of the will of Zeus.",
                                        "https://www.example.com/1")

                        )),
                List.of());
    }

    public static Object mockBacklinkData() throws URISyntaxException {
        return new SearchSiteInfoService.Backlinks(
                "www.example.com",
                4,
                List.of(
                        new GroupedUrlDetails(
                                List.of(
                                        mockUrlDetails("https://www.example.com/", "lorem ipsum"),
                                        mockUrlDetails("https://www.example.com/", "dolor sit"),
                                        mockUrlDetails("https://www.example.com/", "amet quia")
                                        )
                        ),
                        new GroupedUrlDetails(
                                List.of(
                                        mockUrlDetails("https://other.example.com", "single link result")
                                )
                        )
                ),
                List.of(
                        new ResultsPage(1, true, "#"),
                        new ResultsPage(2, false, "#")
                        )
        );
    }

    public static SearchSiteInfoService.Docs mockDocsData() throws URISyntaxException {
        return new SearchSiteInfoService.Docs(
                "www.example.com",
                1,
                List.of(
                        mockUrlDetails("https://www.example.com/", "lorem ipsum"),
                        mockUrlDetails("https://www.example.com/", "dolor sit"),
                        mockUrlDetails("https://www.example.com/", "amet quia")
                ),
                List.of(
                        new ResultsPage(1, true, "#"),
                        new ResultsPage(2, false, "#")
                )
        );
    }

    public static SearchSiteInfoService.ReportDomain mockReportDomain() {
        return new SearchSiteInfoService.ReportDomain(
                "www.example.com",
                1,
                List.of(new SearchFlagSiteService.FlagSiteComplaintModel(
                        "BAD",
                        "2024-10-01",
                        true,
                        "Appealed"
                        )),
                SearchFlagSiteService.categories,
                false
                );
    }

    public static BrowseResultSet mockBrowseResults(int n) {
        List<BrowseResult> results = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            results.add(new BrowseResult(
                    new EdgeUrl("https", new EdgeDomain(i+".example.com"), null, "/", null),
                    i,
                    0.5,
                    true
            ));
        }

        return new BrowseResultSet(results);
    }

    public static SearchSiteInfoService.SiteOverviewModel mockSiteInfoOverview() {
        return new SearchSiteInfoService.SiteOverviewModel(List.of(
                new SearchSiteInfoService.SiteOverviewModel.DiscoveredDomain("www.example.com", "2024-09-23T11:22:33"),
                new SearchSiteInfoService.SiteOverviewModel.DiscoveredDomain("other.example.com", "2023-08-25T11:22:33")
        ));
    }

    public static Object mockCrosstalkModel() throws URISyntaxException {
        return new SearchCrosstalkService.CrosstalkResult(
                "www.example.com",
                "other.example.com",
                List.of(mockUrlDetails("https://www.example.com/some-incredibly-long-address-that-goes-on-and-on", "One document")),
                List.of(mockUrlDetails("https://other.example.com/", "Other document")));
    }

    public static Object mockTrafficReport() {
        List<SearchSiteInfoService.SiteGeneratedRequestsReport.OutgoingRequestsForDomain> requests = new ArrayList<>();
        requests.add(new SearchSiteInfoService.SiteGeneratedRequestsReport.OutgoingRequestsForDomain(
                new EdgeDomain("hotjar.com"),
                List.of(new SearchSiteInfoService.SiteGeneratedRequestsReport.RequestedEndpoint("/foo.js", "POST", DomSampleClassifier.DomSampleClassification.TRACKING)),
                new DDGTDomain(
                        "hotjar.com",
                        new DDGTOwner("Hotjar Ltd", "Hotjar", "https://www.example.com/", "https://www.hotjar.com/"),
                        List.of("Tracking", "Session Replay"),
                        List.of()
                )
        ));
        requests.add(new SearchSiteInfoService.SiteGeneratedRequestsReport.OutgoingRequestsForDomain(
                new EdgeDomain("doubleclick.net"),
                List.of(new SearchSiteInfoService.SiteGeneratedRequestsReport.RequestedEndpoint("/foo.js", "GET", DomSampleClassifier.DomSampleClassification.TRACKING),
                        new SearchSiteInfoService.SiteGeneratedRequestsReport.RequestedEndpoint("/bar.js", "GET", DomSampleClassifier.DomSampleClassification.TRACKING)),
                new DDGTDomain(
                        "doubleclick.net",
                        new DDGTOwner("Doubleclick Inc", "Doubleclick", "https://www.example.com/", "https://www.hotjar.com/"),
                        List.of("CDN", "Advertising"),
                        List.of()
                )
        ));
        requests.add(new SearchSiteInfoService.SiteGeneratedRequestsReport.OutgoingRequestsForDomain(
                new EdgeDomain("sketchy.org"),
                List.of(new SearchSiteInfoService.SiteGeneratedRequestsReport.RequestedEndpoint("/foo.js", "GET", DomSampleClassifier.DomSampleClassification.ADS),
                        new SearchSiteInfoService.SiteGeneratedRequestsReport.RequestedEndpoint("/bar.js", "GET", DomSampleClassifier.DomSampleClassification.CONSENT)),
                new DDGTDomain(
                        "sketchy.org",
                        new DDGTOwner("Doubious AB", "Legit Enterprises", "https://www.example.com/", "https://www.hotjar.com/"),
                        List.of("Malware", "Social - Comment"),
                        List.of()
                )
        ));
        return new SearchSiteInfoService.SiteGeneratedRequestsReport(
                "example.com",
                Map.of(
                        DomSampleClassifier.DomSampleClassification.ADS, 3,
                        DomSampleClassifier.DomSampleClassification.TRACKING, 10
                ),
                requests
        );

    }
}
