package nu.marginalia.converting.sideload;

import lombok.SneakyThrows;
import nu.marginalia.converting.model.*;
import nu.marginalia.keyword.DocumentKeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
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
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/** This code is broken */
@Deprecated()
public class StackexchangeSideloader implements SideloadSource {
    private final StackExchange7zReader reader;
    private final SentenceExtractor sentenceExtractor;
    private final DocumentKeywordExtractor keywordExtractor;
    private final String domainName;

    public StackexchangeSideloader(Path pathTo7zFile,
                                   String domainName,
                                   SentenceExtractor sentenceExtractor,
                                   DocumentKeywordExtractor keywordExtractor
    ) {
        this.domainName = domainName;
        reader = new StackExchange7zReader(pathTo7zFile);
        this.sentenceExtractor = sentenceExtractor;
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
        try {
            var baseIter = reader.postIterator();
            return new Iterator<>() {

                @Override
                public boolean hasNext() {
                    return baseIter.hasNext();
                }

                @Override
                public ProcessedDocument next() {
                    return convert(baseIter.next());
                }
            };
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    private ProcessedDocument convert(StackExchange7zReader.Post post) {
        String fullUrl = "https://" + domainName + "/questions/" + post.id();

        StringBuilder fullHtml = new StringBuilder();
        fullHtml.append("<!DOCTYPE html><html><head><title>").append(post.title()).append("</title></head><body>");
        fullHtml.append("<p>").append(post.title()).append("</p>");
        for (var comment : post.comments()) {
            fullHtml.append("<p>").append(comment.text()).append("</p>");
        }
        fullHtml.append("</body></html>");

        var ret = new ProcessedDocument();
        try {

            var url = new EdgeUrl(fullUrl);
            var doc = Jsoup.parse(fullHtml.toString());
            var dld = sentenceExtractor.extractSentences(doc);

            ret.url = url;
            ret.words = keywordExtractor.extractKeywords(dld, url);
            ret.words.addJustNoMeta("site:"+domainName);
            ret.words.addJustNoMeta("site:"+url.domain.domain);
            ret.words.addJustNoMeta(url.domain.domain);
            ret.words.setFlagOnMetadataForWords(WordFlags.Subjects, post.tags());
            ret.details = new ProcessedDocumentDetails();
            ret.details.pubYear = post.year();
            ret.details.quality = 5;
            ret.details.metadata = new DocumentMetadata(4,
                    PubDate.toYearByte(ret.details.pubYear), (int) -ret.details.quality, EnumSet.noneOf(DocumentFlags.class));
            ret.details.features = EnumSet.noneOf(HtmlFeature.class);
            ret.details.generator = GeneratorType.DOCS;
            ret.details.title = StringUtils.truncate(post.title(), 128);
            ret.details.description = StringUtils.truncate(doc.body().text(), 512);
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


    @Override
    public String getId() {
        return domainName;
    }
}
