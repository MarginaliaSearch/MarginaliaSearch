package nu.marginalia.converting.sideload.stackexchange;

import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.integration.stackexchange.sqlite.StackExchangePostsDb;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class StackexchangeSideloader implements SideloadSource {
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentKeywordExtractor keywordExtractor;
    private final String domainName;

    private final EnumSet<HtmlFeature> applyFeatures = EnumSet.of(HtmlFeature.JS, HtmlFeature.TRACKING);

    private final Path dbFile;

    public StackexchangeSideloader(Path pathToDbFile,
                                   ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                                   DocumentKeywordExtractor keywordExtractor
    ) {
        try {
            this.dbFile = pathToDbFile;
            this.domainName = StackExchangePostsDb.getDomainName(pathToDbFile);
            this.sentenceExtractorProvider = sentenceExtractorProvider;
            this.keywordExtractor = keywordExtractor;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = new EdgeDomain(domainName);
        ret.ip = "127.0.0.1";
        ret.state = DomainIndexingState.ACTIVE;

        if (domainName.contains("stackoverflow.com")) {
            ret.sizeloadSizeAdvice = 5_000_000;
        }
        else {
            ret.sizeloadSizeAdvice = 1000;
        }

        return ret;
    }

    @Override
    public Iterator<ProcessedDocument> getDocumentsStream() {

        var postsReader = new PostsReader();
        Thread readerThread = new Thread(postsReader);
        readerThread.setDaemon(true);
        readerThread.start();

        return new Iterator<>() {

            ProcessedDocument nextModel = null;

            @Override
            public boolean hasNext() {
                if (nextModel != null)
                    return true;
                try {
                    nextModel = postsReader.next();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                return nextModel != null;
            }

            @Override
            public ProcessedDocument next() {
                if (hasNext()) {
                    var ret = nextModel;
                    nextModel = null;
                    return ret;
                }

                throw new IllegalStateException();
            }
        };
    }

    private ProcessedDocument convert(StackExchangePostsDb.CombinedPostModel post) {
        String fullUrl = "https://" + domainName + "/questions/" + post.threadId();

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><title>").append(post.title()).append("</title></head><body>");
        // Add a bogus script tag to make sure we get the JS flag
        fullHtml.append("<script src=\"https://www.example.com/dummy.js\" type=\"text/javascript\"></script>");

        fullHtml.append("<p>").append(post.title()).append("</p>");
        for (var comment : post.bodies()) {
            fullHtml.append("<p>").append(comment).append("</p>");
        }
        fullHtml.append("</body></html>");

        var ret = new ProcessedDocument();
        try {

            var url = new EdgeUrl(fullUrl);
            var doc = Jsoup.parse(fullHtml.toString());
            var dld = sentenceExtractorProvider.get().extractSentences(doc);

            ret.url = url;
            ret.words = keywordExtractor.extractKeywords(dld, new LinkTexts(), url);

            List<String> syntheticTerms = new ArrayList<>(
                    List.of("site:" + domainName,
                            "site:" + url.domain.topDomain,
                            url.domain.topDomain,
                            domainName)
            );
            for (HtmlFeature feature : applyFeatures) {
                syntheticTerms.add(feature.getKeyword());
            }
            ret.words.addAllSyntheticTerms(syntheticTerms);

            if (!post.tags().isBlank()) {
                List<String> subjects = Arrays.asList(post.tags().split(","));
                ret.words.setFlagOnMetadataForWords(WordFlags.Subjects, subjects);
            }

            ret.details = new ProcessedDocumentDetails();
            ret.details.pubYear = post.year();
            ret.details.quality = -10;
            ret.details.metadata = new DocumentMetadata(3,
                    PubDate.toYearByte(ret.details.pubYear),
                    (int) -ret.details.quality,
                    EnumSet.of(DocumentFlags.GeneratorDocs));
            ret.details.features = applyFeatures;
            ret.details.hashCode = dld.localitySensitiveHashCode();
            ret.details.metadata.withSizeAndTopology(10000, 0);

            ret.details.generator = GeneratorType.DOCS;
            ret.details.title = StringUtils.truncate(post.title(), 128);
            ret.details.description = StringUtils.truncate(doc.body().text(), 255);
            ret.details.length = 128;
            ret.details.languageIsoCode = "en"; // FIXME we should run this throguh language detection

            ret.details.format = DocumentFormat.HTML5;
            ret.details.linksExternal = List.of();
            ret.details.linksInternal = List.of();
            ret.state = UrlIndexingState.OK;
            ret.stateReason = "SIDELOAD";
        }
        catch (Exception e) {
            ret.url = EdgeUrl.parse(fullUrl).orElseThrow();
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = "SIDELOAD";
        }

        return ret;
    }

    class PostsReader implements Runnable {
        private final ArrayBlockingQueue<ProcessedDocument> results = new ArrayBlockingQueue<>(16);
        private final SimpleBlockingThreadPool pool = new SimpleBlockingThreadPool("Sideloading Stackexchange", 16, 4);
        volatile boolean isRunning = true;

        public void run() {
            try {
                StackExchangePostsDb.forEachPost(dbFile, this::enqueue);
            }
            finally {
                isRunning = false;
                pool.shutDown();
            }
        }

        private boolean enqueue(StackExchangePostsDb.CombinedPostModel model) {
            try {
                pool.submit(() -> results.put(convert(model)));
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }

            return true;
        }

        public ProcessedDocument next() throws InterruptedException {
            do {
                var next = results.poll(1, TimeUnit.SECONDS);
                if (next != null) {
                    return next;
                }
            } while (!isFinished());

            return null;
        }

        public boolean isFinished() {
            return !isRunning &&
                    results.isEmpty() &&
                    pool.isTerminated();
        }
    }
}
