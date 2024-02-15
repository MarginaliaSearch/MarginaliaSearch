package nu.marginalia.converting.sideload.reddit;

import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDocumentDetails;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.integration.reddit.db.RedditDb;
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
import nu.marginalia.util.ProcessingIterator;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

public class RedditSideloader implements SideloadSource {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RedditSideloader.class);

    private final List<Path> dbFiles;
    private final ThreadLocalSentenceExtractorProvider sentenceExtractorProvider;
    private final DocumentKeywordExtractor keywordExtractor;

    public RedditSideloader(List<Path> listToDbFiles,
                            ThreadLocalSentenceExtractorProvider sentenceExtractorProvider,
                            DocumentKeywordExtractor keywordExtractor) {
        this.dbFiles = listToDbFiles;
        this.sentenceExtractorProvider = sentenceExtractorProvider;
        this.keywordExtractor = keywordExtractor;
    }

    @Override
    public ProcessedDomain getDomain() {
        var ret = new ProcessedDomain();

        ret.domain = new EdgeDomain("old.reddit.com");
        ret.ip = "127.0.0.1";
        ret.state = DomainIndexingState.ACTIVE;

        ret.sizeloadSizeAdvice = 5_000_000;

        return ret;
    }

    @Override
    public Iterator<ProcessedDocument> getDocumentsStream() {
        return ProcessingIterator.factory(24, 16).create((taskConsumer) -> {
            DomainLinks domainLinks = new DomainLinks();

            for (var dbFile : dbFiles) {
                try (var submissions = RedditDb.getSubmissions(dbFile)) {
                    while (submissions.hasNext()) {
                        var entry = submissions.next();
                        taskConsumer.accept(() ->
                                convertDocument(entry.selftext,
                                        entry.subreddit,
                                        entry.title,
                                        entry.author,
                                        entry.permalink,
                                        entry.created_utc,
                                        entry.score,
                                        domainLinks)
                        );
                    }
                } catch (Exception e) {
                    logger.error("Error reading db file", e);
                }

                try (var comments = RedditDb.getComments(dbFile)) {
                    while (comments.hasNext()) {
                        var entry = comments.next();
                        taskConsumer.accept(() ->
                                convertDocument(entry.body,
                                        entry.subreddit,
                                        entry.title,
                                        entry.author,
                                        entry.permalink,
                                        entry.created_utc,
                                        entry.score,
                                        domainLinks)
                        );
                    }
                } catch (Exception e) {
                    logger.error("Error reading db file", e);
                }
            }
        });
    }

    private ProcessedDocument convertDocument(String body,
                                              String subreddit,
                                              String title,
                                              String author,
                                              String permalink,
                                              int createdUtc,
                                              int score,
                                              DomainLinks domainLinks) throws URISyntaxException {
        String fullUrl = "https://old.reddit.com" + permalink;

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><title>").append(title).append("</title></head><body>");
        fullHtml.append("<h1>").append(title).append("</h1>");
        fullHtml.append("<p>").append(body).append("</p>");
        fullHtml.append("</body></html>");

        var ret = new ProcessedDocument();
        try {

            var url = new EdgeUrl(fullUrl);
            var doc = Jsoup.parse(fullHtml.toString());
            var dld = sentenceExtractorProvider.get().extractSentences(doc);

            ret.url = url;
            ret.words = keywordExtractor.extractKeywords(dld, url);

            ret.words.addAllSyntheticTerms(List.of(
                    "js:true",
                    "site:reddit.com",
                    "site:old.reddit.com",
                    "site:www.reddit.com",
                    "special:ads",
                    "special:tracking",
                    "generator:forum",
                    subreddit
            ));

            ret.words.add(subreddit, WordFlags.Subjects.asBit());
            ret.words.add("reddit",
                    WordFlags.ExternalLink.asBit()
                        | WordFlags.Subjects.asBit()
                        | WordFlags.Synthetic.asBit()
                        | WordFlags.NamesWords.asBit());
            ret.words.add(subreddit.toLowerCase(),
                    WordFlags.ExternalLink.asBit()
                    | WordFlags.NamesWords.asBit()
                    | WordFlags.Synthetic.asBit()
            );
            if (!"[deleted]".equals(author))
                ret.words.add(author, WordFlags.NamesWords.asBit() | WordFlags.Synthetic.asBit());

            var date = LocalDate.ofInstant(
                    Instant.ofEpochSecond(createdUtc),
                    ZoneOffset.UTC);
            int year = date.getYear();

            ret.details = new ProcessedDocumentDetails();
            ret.details.pubYear = year;
            ret.details.quality = -5;
            ret.details.metadata = new DocumentMetadata(3,
                    PubDate.toYearByte(year),
                    (int) -ret.details.quality,
                    EnumSet.of(DocumentFlags.GeneratorForum));
            ret.details.features = EnumSet.of(HtmlFeature.JS, HtmlFeature.TRACKING);

            ret.details.metadata.withSizeAndTopology(10000, score);

            ret.details.generator = GeneratorType.DOCS;
            ret.details.title = StringUtils.truncate(STR."[/r/\{subreddit}] \{title}", 128);
            ret.details.description = StringUtils.truncate(body, 255);
            ret.details.length = 128;

            ret.details.standard = HtmlStandard.HTML5;
            ret.details.feedLinks = List.of();
            ret.details.linksExternal = List.of();
            ret.details.linksInternal = List.of();
            ret.state = UrlIndexingState.OK;
            ret.stateReason = "SIDELOAD";
        }
        catch (Exception e) {
            logger.warn("Failed to process document", e);
            ret.url = new EdgeUrl(fullUrl);
            ret.state = UrlIndexingState.DISQUALIFIED;
            ret.stateReason = "SIDELOAD";
        }
        return ret;
    };
}
