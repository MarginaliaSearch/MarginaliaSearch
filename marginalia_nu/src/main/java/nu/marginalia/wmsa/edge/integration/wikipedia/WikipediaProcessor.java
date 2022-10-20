package nu.marginalia.wmsa.edge.integration.wikipedia;

import nu.marginalia.util.language.processing.DocumentKeywordExtractor;
import nu.marginalia.util.language.processing.SentenceExtractor;
import nu.marginalia.util.language.processing.model.KeywordMetadata;
import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.wikipedia.model.WikipediaArticle;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainLink;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WikipediaProcessor {
    private final LinkParser linkParser = new LinkParser();

    private final SentenceExtractor sentenceExtractor;
    private final DocumentKeywordExtractor documentKeywordExtractor;

    public WikipediaProcessor(SentenceExtractor sentenceExtractor, DocumentKeywordExtractor documentKeywordExtractor) {
        this.sentenceExtractor = sentenceExtractor;
        this.documentKeywordExtractor = documentKeywordExtractor;
    }


    public BasicDocumentData process(WikipediaArticle post) {

        final var docUrl = post.getUrl();
        final var doc = Jsoup.parseBodyFragment(post.body);

        String title = StringUtils.abbreviate(doc.getElementsByTag("title").text(), 255);
        String description = getSummary(doc);

        EdgeDomainLink[] domainLinks = getDomainLinks(docUrl, doc);

        var dld = sentenceExtractor.extractSentences(doc);
        var keywords = documentKeywordExtractor.extractKeywords(dld, new KeywordMetadata(15));

        keywords.get(IndexBlock.Meta).addJustNoMeta("site:"+post.getUrl().domain);
        keywords.get(IndexBlock.Words_1).addJustNoMeta("site:"+post.getUrl().domain);
        keywords.get(IndexBlock.Words_1).addJustNoMeta("special:stackoverflow");
        keywords.get(IndexBlock.Meta).addJustNoMeta("special:stackoverflow");
        keywords.get(IndexBlock.Meta).addJustNoMeta("js:true");

        return new BasicDocumentData(docUrl, title, description, post.body.hashCode(), keywords, domainLinks,
                dld.totalNumWords());

    }

    private String getSummary(Document doc) {
        doc = doc.clone();
        doc.select("table,sup,.reference").remove();
        return StringUtils.abbreviate(doc.select("#bodyContent p").text(), 255);
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
