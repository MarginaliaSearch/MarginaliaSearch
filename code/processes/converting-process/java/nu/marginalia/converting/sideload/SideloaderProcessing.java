package nu.marginalia.converting.sideload;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.html.HtmlStandard;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.EnumSet;
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
                                             GeneratorType type,
                                             DocumentClass documentClass,
                                             LinkTexts linkTexts,
                                             int pubYear,
                                             int size) throws URISyntaxException {
        var crawledDoc = new CrawledDocument(
                "synthetic",
                url,
                "text/html",
                LocalDateTime.now().toString(),
                200,
                "OK",
                "NP",
                "",
                body.getBytes(StandardCharsets.UTF_8),
                false,
                null,
                null
        );

        // Give the document processing preferential treatment if this is a sideloaded wiki, since we
        // truncate the document to the first paragraph, which typically is too short to be included
        // on its own.

        var ret = new ProcessedDocument();
        try {
            var details = htmlProcessorPlugin.createDetails(crawledDoc, linkTexts, documentClass);

            ret.words = details.words();

            for (String keyword : extraKeywords)
                ret.words.addMeta(keyword, WordFlags.Subjects.asBit());

            if (type == GeneratorType.WIKI) {
                ret.words.addAllSyntheticTerms(List.of("generator:wiki"));
            } else if (type == GeneratorType.DOCS) {
                ret.words.addAllSyntheticTerms(List.of("generator:docs"));
            } else if (type == GeneratorType.FORUM) {
                ret.words.addAllSyntheticTerms(List.of("generator:forum"));
            }
            ret.details = details.details();

            // Add a few things that we know about the document
            // that we can't get from the sideloaded data since it's
            // so stripped down

            ret.details.standard = HtmlStandard.HTML5;
            ret.details.pubYear = pubYear;
            ret.details.features.add(HtmlFeature.JS);
            ret.details.features.add(HtmlFeature.TRACKING);
            ret.details.quality = -4.5;
            ret.details.generator = type;

            ret.details.metadata = new DocumentMetadata(3,
                            PubDate.toYearByte(pubYear),
                            (int) -ret.details.quality,
                            switch (type) {
                                case WIKI -> EnumSet.of(DocumentFlags.GeneratorWiki, DocumentFlags.Sideloaded);
                                case DOCS -> EnumSet.of(DocumentFlags.GeneratorDocs, DocumentFlags.Sideloaded);
                                default -> EnumSet.noneOf(DocumentFlags.class);
                            });


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
