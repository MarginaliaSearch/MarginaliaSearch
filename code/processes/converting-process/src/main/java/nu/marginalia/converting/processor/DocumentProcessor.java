package nu.marginalia.converting.processor;

import com.google.inject.Inject;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.converting.model.DisqualifiedException;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.processor.plugin.AbstractDocumentProcessorPlugin;
import nu.marginalia.converting.processor.plugin.HtmlDocumentProcessorPlugin;
import nu.marginalia.converting.processor.plugin.PlainTextDocumentProcessorPlugin;
import nu.marginalia.model.EdgeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.*;

public class DocumentProcessor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Set<String> acceptedContentTypes = Set.of("application/xhtml+xml",
            "application/xhtml",
            "text/html",
            "text/plain");


    private final List<AbstractDocumentProcessorPlugin> processorPlugins = new ArrayList<>();

    @Inject
    public DocumentProcessor(HtmlDocumentProcessorPlugin htmlDocumentProcessorPlugin,
                             PlainTextDocumentProcessorPlugin plainTextDocumentProcessorPlugin)
    {

        processorPlugins.add(htmlDocumentProcessorPlugin);
        processorPlugins.add(plainTextDocumentProcessorPlugin);
    }

    public ProcessedDocument process(CrawledDocument crawledDocument,
                                     EdgeDomain domain,
                                     DomainLinks externalDomainLinks,
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

            processDocument(crawledDocument, documentClass, documentDecorator, externalDomainLinks, ret);
        }
        catch (DisqualifiedException ex) {
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = ex.reason.toString();
            logger.debug("Disqualified {}: {}", ret.url, ex.reason);
        }
        catch (Exception ex) {
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = DisqualifiedException.DisqualificationReason.PROCESSING_EXCEPTION.toString();
            logger.info("Failed to convert " + crawledDocument.url, ex);
        }

        return ret;
    }

    private void processDocument(CrawledDocument crawledDocument, DocumentClass documentClass, DocumentDecorator documentDecorator, DomainLinks externalDomainLinks, ProcessedDocument ret) throws URISyntaxException, DisqualifiedException {

        var crawlerStatus = CrawlerDocumentStatus.valueOf(crawledDocument.crawlerStatus);
        if (crawlerStatus != CrawlerDocumentStatus.OK) {
            throw new DisqualifiedException(crawlerStatus);
        }

        if (AcceptableAds.hasAcceptableAdsHeader(crawledDocument)) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.ACCEPTABLE_ADS);
        }

        if (!isAcceptedContentType(crawledDocument)) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.CONTENT_TYPE);
        }

        ret.state = crawlerStatusToUrlState(crawledDocument.crawlerStatus, crawledDocument.httpStatus);

        final var plugin = findPlugin(crawledDocument);

        AbstractDocumentProcessorPlugin.DetailsWithWords detailsWithWords = plugin.createDetails(crawledDocument, documentClass);

        ret.details = detailsWithWords.details();
        ret.words = detailsWithWords.words();

        documentDecorator.apply(ret, externalDomainLinks);

        if (Boolean.TRUE.equals(crawledDocument.hasCookies)
         && ret.details != null
         && ret.details.features != null)
        {
            ret.details.features.add(HtmlFeature.COOKIES);
        }

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
