package nu.marginalia.converting.sideload.reddit;

import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.converting.model.GeneratorType;
import nu.marginalia.converting.model.ProcessedDocument;
import nu.marginalia.converting.model.ProcessedDomain;
import nu.marginalia.converting.processor.DocumentClass;
import nu.marginalia.converting.sideload.SideloadSource;
import nu.marginalia.converting.sideload.SideloaderProcessing;
import nu.marginalia.integration.reddit.db.RedditDb;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.DomainIndexingState;
import nu.marginalia.model.idx.WordFlags;
import nu.marginalia.util.ProcessingIterator;
import org.apache.commons.lang3.StringUtils;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RedditSideloader implements SideloadSource {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RedditSideloader.class);

    private final List<Path> dbFiles;
    private final AnchorTagsSourceFactory anchorTagsSourceFactory;
    private final AnchorTextKeywords anchorTextKeywords;
    private final SideloaderProcessing sideloaderProcessing;

    public RedditSideloader(List<Path> listToDbFiles,
                            AnchorTagsSourceFactory anchorTagsSourceFactory,
                            AnchorTextKeywords anchorTextKeywords,
                            SideloaderProcessing sideloaderProcessing) {
        this.dbFiles = listToDbFiles;
        this.anchorTagsSourceFactory = anchorTagsSourceFactory;
        this.anchorTextKeywords = anchorTextKeywords;
        this.sideloaderProcessing = sideloaderProcessing;
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

        int pubYear = LocalDate
                .ofInstant(Instant.ofEpochSecond(createdUtc), ZoneOffset.UTC)
                .getYear();

        String fullHtml = STR."""
            <!DOCTYPE html>
            <html>
            <head>
              <title>\{title}</title>
              <script src="https://www.example.com/dummy.js" type="text/javascript"></script>
            </head>
            <body>
              <h1>\{title}</h1>
              <article>
                <p>\{body}</p>
              </article>
            </body>
            </html>
            """;

        List<String> extraKeywords = new ArrayList<>();

        extraKeywords.add("reddit");
        extraKeywords.add(subreddit);
        extraKeywords.add("r/" + subreddit);

        if (!StringUtils.isBlank(author) && !author.equals("[deleted]")) {
            extraKeywords.add(author);
        }

        var doc = sideloaderProcessing
                .processDocument(fullUrl,
                        fullHtml,
                        List.of("reddit"),
                        domainLinks,
                        GeneratorType.FORUM,
                        DocumentClass.SIDELOAD,
                        pubYear,
                        10_000_000);


        if (doc.isProcessedFully()) {
            for (String url : List.of(
                    STR."https://old.reddit.com/r/\{permalink}",
                    STR."https://www.reddit.com/r/\{permalink}",
                    STR."https://reddit.com/r/\{permalink}"
            )) {
                EdgeUrl.parse(url)
                        .map(parsed -> anchorTextKeywords.getAnchorTextKeywords(domainLinks, parsed))
                        .filter(parsed -> !parsed.isEmpty())
                        .ifPresent(doc.words::addAnchorTerms);
            }

            for (var keyword : extraKeywords) {
                doc.words.add(keyword, WordFlags.Subjects.asBit());
            }

            // Insert topology information
            doc.details.metadata.withSizeAndTopology(50_000_000, score);
        }


        return doc;
    }
}
