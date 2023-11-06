package nu.marginalia.converting.sideload;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.idx.WordFlags;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.List;

@Singleton
public class SideloaderProcessing {
    private final HtmlDocumentProcessorPlugin htmlProcessorPlugin;

    @Inject
    public SideloaderProcessing(HtmlDocumentProcessorPlugin htmlProcessorPlugin) {
        this.htmlProcessorPlugin = htmlProcessorPlugin;
    }

    public ProcessedDocument processDocument(String url,
                                             String body,
                                             List<String> extraKeywords,
                                             DomainLinks domainLinks,
                                             int size) throws URISyntaxException {
        var crawledDoc = new CrawledDocument(
                "encyclopedia.marginalia.nu",
                url,
                "text/html",
                LocalDateTime.now().toString(),
                200,
                "OK",
                "NP",
                "",
                body,
                Integer.toHexString(url.hashCode()),
                url,
                "",
                "SIDELOAD"
        );

        var ret = new ProcessedDocument();
        try {
            var details = htmlProcessorPlugin.createDetails(crawledDoc);

            ret.words = details.words();

            for (String keyword : extraKeywords)
                ret.words.add(keyword, WordFlags.Subjects.asBit());

            ret.details = details.details();

            // FIXME (2023-11-06): For encyclopedia loading, this will likely only work when the domain specified is en.wikipedia.org
            // We don't have access to the article name at this point to generate an equivalent URL...  It's not a huge
            // deal but something to keep in mind
            int topology = domainLinks.countForUrl(new EdgeUrl(url));

            ret.details.metadata = ret.details.metadata
                    .withSizeAndTopology(size, topology);

            ret.url = new EdgeUrl(url);
            ret.state = UrlIndexingState.OK;
            ret.stateReason = "SIDELOAD";
        }
        catch (Exception e) {
            ret.url = new EdgeUrl(url);
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = "SIDELOAD";
        }

        return ret;
    }
}
