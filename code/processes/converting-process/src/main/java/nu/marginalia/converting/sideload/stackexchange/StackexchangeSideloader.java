package nu.marginalia.converting.sideload.stackexchange;

import lombok.SneakyThrows;
import nu.marginalia.converting.model.*;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.integration.stackexchange.sqlite.StackExchangePostsDb;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.ThreadLocalSentenceExtractorProvider;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.crawl.HtmlFeature;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.model.crawl.UrlIndexingState;
import nu.marginalia.model.html.HtmlStandard;
import nu.marginalia.model.idx.DocumentFlags;
import nu.marginalia.model.idx.DocumentMetadata;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.util.SimpleBlockingThreadPool;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

public class StackexchangeSideloader implements SideloadSource {
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentKeywordExtractor keywordExtractor;
    private final String domainName;

    private final Path dbFile;

    @SneakyThrows
    public StackexchangeSideloader(Path pathToDbFile,
                                   ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                                   DocumentKeywordExtractor keywordExtractor
    ) {
        this.dbFile = pathToDbFile;
        this.domainName = StackExchangePostsDb.getDomainName(pathToDbFile);
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.keywordExtractor = keywordExtractor;
    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = new EdgeDomain(domainName);
        ret.ip = "127.0.0.1";
        ret.state = DomainIndexingState.ACTIVE;

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

            @SneakyThrows
            @Override
            public boolean hasNext() {
                if (nextModel != null)
                    return true;
                nextModel = postsReader.next();

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

    @SneakyThrows
    private ProcessedDocument convert(StackExchangePostsDb.CombinedPostModel post) {
        String fullUrl = "https://" + domainName + "/questions/" + post.threadId();

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><title>").append(post.title()).append("</title></head><body>");
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
            ret.words = keywordExtractor.extractKeywords(dld, url);
            ret.words.addAllSyntheticTerms(List.of(
                    "site:" + domainName,
                    "site:" + url.domain.domain,
                    url.domain.domain
            ));

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
            ret.details.features = EnumSet.of(HtmlFeature.JS, HtmlFeature.TRACKING);

            ret.details.metadata.withSizeAndTopology(10000, 0);

            ret.details.generator = GeneratorType.DOCS;
            ret.details.title = StringUtils.truncate(post.title(), 128);
            ret.details.description = StringUtils.truncate(doc.body().text(), 255);
            ret.details.length = 128;

            ret.details.standard = HtmlStandard.HTML5;
            ret.details.feedLinks = List.of();
            ret.details.linksExternal = List.of();
            ret.details.linksInternal = List.of();
            ret.state = UrlIndexingState.OK;
            ret.stateReason = "SIDELOAD";
        }
        catch (Exception e) {
            ret.url = new EdgeUrl(fullUrl);
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

        @SneakyThrows
        private boolean enqueue(StackExchangePostsDb.CombinedPostModel model) {
            pool.submit(() -> results.put(convert(model)));

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
