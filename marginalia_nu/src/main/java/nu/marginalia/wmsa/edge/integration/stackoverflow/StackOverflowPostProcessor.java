package nu.marginalia.wmsa.edge.integration.stackoverflow;

import com.google.inject.Inject;
import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.util.language.processing.DocumentKeywordExtractor;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.stackoverflow.model.StackOverflowPost;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StackOverflowPostProcessor {
    private final LinkParser linkParser = new LinkParser();

    private final SentenceExtractor sentenceExtractor;
    private final DocumentKeywordExtractor documentKeywordExtractor;

    @Inject
    public StackOverflowPostProcessor(SentenceExtractor sentenceExtractor, DocumentKeywordExtractor documentKeywordExtractor) {
        this.sentenceExtractor = sentenceExtractor;
        this.documentKeywordExtractor = documentKeywordExtractor;
    }

    public BasicDocumentData process(StackOverflowPost post) {

        final var docUrl = post.getUrl();
        final var doc = Jsoup.parseBodyFragment("<title>"+post.getTitle()+"</title>" + post.getFullBody());

        EdgeDomainLink[] domainLinks = getDomainLinks(docUrl, doc);

        for (var tag : doc.getElementsByTag("code")) {
            if (tag.text().length() > 32) {
                tag.remove();
            }
        }

        var dld = sentenceExtractor.extractSentences(doc);
        var keywords = documentKeywordExtractor.extractKeywords(dld);

        keywords.get(IndexBlock.Meta).addJust("site:"+post.getUrl().domain);
        keywords.get(IndexBlock.Words).addJust("site:"+post.getUrl().domain);
        keywords.get(IndexBlock.Words).addJust("special:wikipedia");
        keywords.get(IndexBlock.Meta).addJust("special:wikipedia");
        keywords.get(IndexBlock.Meta).addJust("js:true");

        String title = StringUtils.abbreviate(post.getTitle(), 255);
        String description = StringUtils.abbreviate(Jsoup.parseBodyFragment(post.getJustBody()).text(), 255);

        return new BasicDocumentData(docUrl, title, description, post.fullBody.hashCode(), keywords, domainLinks,
                dld.totalNumWords());

    }

    private EdgeDomainLink[] getDomainLinks(EdgeUrl docUrl, Document doc) {
        List<EdgeUrl> links = new ArrayList<>(10);

        for (var tag : doc.getElementsByTag("a")) {
            if (!tag.hasAttr("href")) {
                continue;
            }
            String href = tag.attr("href");
            if (href.length()<10 || !href.contains(".") || !href.contains("://")) {
                continue;
            }

            linkParser.parseLink(docUrl, tag)
                    .filter(url -> !Objects.equals(docUrl.getDomain(), url.getDomain()))
                    .ifPresent(links::add);
        }

        return links.stream().map(EdgeUrl::getDomain).map(domain -> new EdgeDomainLink(docUrl.domain, domain))
                .distinct().toArray(EdgeDomainLink[]::new);
    }

}
