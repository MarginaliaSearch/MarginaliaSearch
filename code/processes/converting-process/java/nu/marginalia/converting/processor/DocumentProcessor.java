package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.classifier.AcceptableAds;
import nu.marginalia.converting.processor.plugin.AbstractDocumentProcessorPlugin;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;
import nu.marginalia.converting.processor.plugin.PdfDocumentProcessorPlugin;
import nu.marginalia.converting.processor.plugin.PlainTextDocumentProcessorPlugin;
import nu.marginalia.domclassifier.DomSampleClassifier;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.model.crawldata.CrawlerDocumentStatus;
import nu.marginalia.model.idx.WordFlags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DocumentProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Set<String> acceptedContentTypes = Set.of("application/xhtml+xml",
            "application/xhtml",
            "text/html",
            "text/plain",
            "application/pdf");

    private final Marker converterAuditMarker = MarkerFactory.getMarker("CONVERTER");

    private final List<AbstractDocumentProcessorPlugin> processorPlugins = new ArrayList<>();
    private final AnchorTextKeywords anchorTextKeywords;

    @Inject
    public DocumentProcessor(HtmlDocumentProcessorPlugin htmlDocumentProcessorPlugin,
                             PlainTextDocumentProcessorPlugin plainTextDocumentProcessorPlugin,
                             PdfDocumentProcessorPlugin pdfDocumentProcessorPlugin,
                             AnchorTextKeywords anchorTextKeywords)
    {
        this.anchorTextKeywords = anchorTextKeywords;

        processorPlugins.add(htmlDocumentProcessorPlugin);
        processorPlugins.add(plainTextDocumentProcessorPlugin);
        processorPlugins.add(pdfDocumentProcessorPlugin);
    }

    public ProcessedDocument process(CrawledDocument crawledDocument,
                                     EdgeDomain domain,
                                     DomainLinks externalDomainLinks,
                                     Set<DomSampleClassifier.DomSampleClassification> domSampleClassifications,
                                     DocumentDecorator documentDecorator) {
        ProcessedDocument ret = new ProcessedDocument();

        try {
            // We must always provide the URL, even if we don't process the document
            ret.url = getDocumentUrl(crawledDocument);

            if (!Objects.equals(ret.url.domain, domain)) {
                ret.state = UrlIndexingState.DISQUALIFIED;
                ret.stateReason = DisqualifiedException.DisqualificationReason.PROCESSING_EXCEPTION.toString();
                return ret;
            }

            DocumentClass documentClass = switch (externalDomainLinks.countForUrl(ret.url)) {
                case 0 -> DocumentClass.NORMAL;
                case 1 -> DocumentClass.EXTERNALLY_LINKED_ONCE;
                default -> DocumentClass.EXTERNALLY_LINKED_MULTI;
            };

            var crawlerStatus = CrawlerDocumentStatus.valueOf(crawledDocument.crawlerStatus);

            if (crawlerStatus != CrawlerDocumentStatus.OK)
                throw new DisqualifiedException(crawlerStatus);
            if (AcceptableAds.hasAcceptableAdsHeader(crawledDocument))
                throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.ACCEPTABLE_ADS);
            if (!isAcceptedContentType(crawledDocument))
                throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.CONTENT_TYPE);

            ret.state = crawlerStatusToUrlState(crawledDocument.crawlerStatus, crawledDocument.httpStatus);

            LinkTexts linkTexts = anchorTextKeywords.getAnchorTextKeywords(externalDomainLinks, ret.url);

            var detailsWithWords =
                    findPlugin(crawledDocument)
                            .createDetails(crawledDocument, linkTexts, domSampleClassifications, documentClass);

            ret.details = detailsWithWords.details();
            ret.words = detailsWithWords.words();

            if (ret.url.path.equals("/")) {
                ret.words.addMeta("special:root", WordFlags.Synthetic.asBit());
            }

            documentDecorator.apply(ret);

            if (Boolean.TRUE.equals(crawledDocument.hasCookies)
                    && ret.details != null
                    && ret.details.features != null)
            {
                ret.details.features.add(HtmlFeature.COOKIES);
            }
        }
        catch (DisqualifiedException ex) {
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = ex.reason.toString();
            logger.info(converterAuditMarker, "Disqualified {}: {}", ret.url, ex.reason);
        }
        catch (Exception ex) {
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = DisqualifiedException.DisqualificationReason.PROCESSING_EXCEPTION.toString();
            logger.warn(converterAuditMarker, "Failed to convert {}: {}", crawledDocument.url, ex.getClass().getSimpleName());
        }

        return ret;
    }

    private AbstractDocumentProcessorPlugin findPlugin(CrawledDocument crawledDocument) throws DisqualifiedException {
        for (var plugin : processorPlugins) {
            if (plugin.isApplicable(crawledDocument))
                return plugin;
        }

        throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.CONTENT_TYPE);
    }


    private EdgeUrl getDocumentUrl(CrawledDocument crawledDocument)
            throws URISyntaxException
    {
        return new EdgeUrl(crawledDocument.url);
    }

    public static boolean isAcceptedContentType(CrawledDocument crawledDocument) {
        if (crawledDocument.contentType == null) {
            return false;
        }

        var ct = crawledDocument.contentType;

        if (acceptedContentTypes.contains(ct))
            return true;

        if (ct.contains(";")) {
            return acceptedContentTypes.contains(ct.substring(0, ct.indexOf(';')));
        }
        return false;
    }

    private UrlIndexingState crawlerStatusToUrlState(String crawlerStatus, int httpStatus) {
        return switch (CrawlerDocumentStatus.valueOf(crawlerStatus)) {
            case OK -> httpStatus < 300 ? UrlIndexingState.OK : UrlIndexingState.DEAD;
            case REDIRECT -> UrlIndexingState.REDIRECT;
            default -> UrlIndexingState.DEAD;
        };
    }

}
