package nu.marginalia.wmsa.edge.crawler.domain.processor;

//import net.sf.classifier4J.summariser.SimpleSummariser;

import nu.marginalia.wmsa.edge.crawler.domain.LinkParser;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.language.WordPatterns;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.KeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.DocumentSentence;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard.UNKNOWN;

@Singleton
public class HtmlProcessor {
    private static final LanguageFilter languageFilter = new LanguageFilter();
    private static final Logger logger = LoggerFactory.getLogger(HtmlProcessor.class);

    private final DocumentKeywordExtractor documentKeywordExtractor;
    private final SentenceExtractor sentenceExtractor;
    private final KeywordExtractor keywordExtractor = new KeywordExtractor();

    private static final Set<String> filthTable = Set.of(
            "xxx", "sex", "anal", "sexy",
            "bdsm", "fetish", "porn", "camgirls", "dildo",
            "gangbang", "buttplug", "orgasm", "vibrator",
            "cameltoe", "download", "iso", "botox", "torrent",
            "jackpot", "vegas", "casino", "coinbase", "poloniex",
            "myetherwallet", "ethereum", "binance", "bitcoin",
            "litecoin", "seo", "serp"

    );

    private static final LinkParser linkParser = new LinkParser();

    @Inject
    public HtmlProcessor(DocumentKeywordExtractor documentKeywordExtractor, SentenceExtractor sentenceExtractor) {
        this.documentKeywordExtractor = documentKeywordExtractor;
        this.sentenceExtractor = sentenceExtractor;
    }

    public EdgePageContent processHtmlPage(EdgeRawPageContents rawPageContent, Document parsed) {

        var parsed2 = parsed.clone();
        final String text = parsed.getElementsByTag("body").text();

        if (languageFilter.isBlockedUnicodeRange(text)) {
            logger.debug("Skipping {} , foreign unicode ranges in excessive presence", rawPageContent.url);
            return null;
        }

        int rawLength = rawPageContent.data.length();
        int scriptTags = getScriptPenalty(parsed);
        int textLength = text.length();

        EdgeHtmlStandard htmlStandard = HtmlStandardExtractor.parseDocType(parsed.documentType());
        if (UNKNOWN.equals(htmlStandard)) {
            htmlStandard = HtmlStandardExtractor.sniffHtmlStandard(parsed);
        }

        var dld = sentenceExtractor.extractSentences(parsed.clone());
        var keywords = documentKeywordExtractor.extractKeywords(dld);

        var featureSet = getFeatureSet(parsed, scriptTags, rawPageContent.hasCookies);
        addTags(keywords, htmlStandard, rawPageContent.url, featureSet);

        final String title = Optional.ofNullable(parsed.getElementsByTag("title"))
                .map(Elements::first)
                .map(Element::text)
                .or(() -> Optional.ofNullable(parsed.getElementsByTag("h1").first()).map(Element::text))
                .or(() -> Optional.ofNullable(parsed.getElementsByTag("h2").first()).map(Element::text))
                .or(() -> Optional.ofNullable(parsed.getElementsByTag("h3").first()).map(Element::text))
                .or(() -> {
                    if (dld.sentences.length > 0) return Optional.of(dld.sentences[0].originalSentence);
                    return Optional.empty();
                })
                .map(str -> StringUtils.truncate(str, 128))
                .orElseGet(rawPageContent.url::toString);

        int wc = dld.totalNumWords();

        var bodyWords = keywords.get(IndexBlock.Words);
        if (wc > 100) {
            double languageAgreement = languageFilter.dictionaryAgreement(dld);
            if (languageAgreement < 0.01 || (wc > 200 && languageAgreement < 0.05) ) {
                logger.debug("Skipping {} , poor language agreement {}", rawPageContent.url, languageAgreement);
                return null;
            }
        }

        double smutCoefficient = bodyWords.words.stream().filter(filthTable::contains).count();

        Set<String> summaryKeywords = new HashSet<>();

        summaryKeywords.addAll(keywords.get(IndexBlock.Low).words);
        summaryKeywords.addAll(keywords.get(IndexBlock.Middle).words);
        summaryKeywords.addAll(keywords.get(IndexBlock.Top).words);
        summaryKeywords.addAll(keywords.get(IndexBlock.Title).words);

        var description = extractSummary(parsed2, summaryKeywords)
                .or(() -> getOgDescription(parsed2))
                .or(() -> getMetaDescription(parsed2));

        int totalWords = Arrays.stream(dld.sentences).mapToInt(DocumentSentence::length).sum();

        final var metadata = new EdgePageMetadata(
                HtmlFeature.encode(featureSet), scriptTags, rawLength, textLength, wc,
                title, description.orElse(""), smutCoefficient,
                totalWords, htmlStandard);

        Map<EdgeUrl, Set<String>> linkWords = extractLinkWords(keywords, rawPageContent.getUrl(), parsed);

        return new EdgePageContent(rawPageContent.url, keywords, linkWords, metadata, rawPageContent.getData().hashCode(),
                rawPageContent.ip);
    }

    List<String> trackers = List.of("adform.net",
            "connect.facebook",
            "googletagmanager.com",
            "googlesyndication.com",
            "google.com",
            "twitter.com",
            "smartadserver.com",
            "doubleclick.com",
            "2mdn.com",
            "dmtry.com",
            "bing.com",
            "msn.com",
            "amazon-adsystem.com",
            "alexametrics.com",
            "rubiconproject.com",
            "chango.com",
            "d5nxst8fruw4z.cloudfront.net",
            "d31qbv1cthcecs.cloudfront.net",
            "linkedin.com");

    private Set<HtmlFeature> getFeatureSet(Document parsed, int scriptTags, boolean cookies) {
        Set<HtmlFeature> features = new HashSet<>();

        if (scriptTags > 0) {
            features.add(HtmlFeature.JS);
        }
        if (!parsed.getElementsByTag("object").isEmpty()
            || !parsed.getElementsByTag("audio").isEmpty()
            || !parsed.getElementsByTag("video").isEmpty()) {
            features.add(HtmlFeature.MEDIA);
        }
        if (parsed.getElementsByTag("script").stream()
                .filter(tag -> tag.attr("src") != null)
                .anyMatch(tag -> trackers.stream().anyMatch(tracker -> tag.attr("src").contains(tracker)))) {
            features.add(HtmlFeature.TRACKING);
        }
        if (parsed.getElementsByTag("script").html().contains("google-analytics.com")) {
            features.add(HtmlFeature.TRACKING);
        }
        if (parsed.getElementsByTag("a").stream().map(e -> e.attr("href"))
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .anyMatch(href ->
                        href.contains("amzn.to/") || href.contains("amazon.com/"))) {
            features.add(HtmlFeature.AFFILIATE_LINK);
        }
        if (cookies) {
            features.add(HtmlFeature.COOKIES);
        }

        return features;
    }

    private void addTags(EdgePageWordSet wordSet, EdgeHtmlStandard htmlStandard, EdgeUrl url, Set<HtmlFeature> features) {
        List<String> tagWords = new ArrayList<>();
        tagWords.add("format:"+htmlStandard.toString().toLowerCase());
        tagWords.add("site:"+url.domain.toString().toLowerCase());
        tagWords.add("proto:"+url.proto.toLowerCase());
        tagWords.add("js:" + Boolean.toString(features.contains(HtmlFeature.JS)).toLowerCase());
        if (features.contains(HtmlFeature.MEDIA)) {
            tagWords.add("special:media");
        }
        if (features.contains(HtmlFeature.TRACKING)) {
            tagWords.add("special:tracking");
        }
        if (features.contains(HtmlFeature.AFFILIATE_LINK)) {
            tagWords.add("special:affiliate");
        }
        if (features.contains(HtmlFeature.COOKIES)) {
            tagWords.add("special:cookies");
        }
        wordSet.append(IndexBlock.Meta, tagWords);
        wordSet.append(IndexBlock.Words, tagWords);
    }

    private int getScriptPenalty(Document parsed) {
        var scriptTags = parsed.getElementsByTag("script");
        String scriptText = scriptTags.html();
        int badScript = 0;
        if (scriptText.contains(".createElement(")) {
            badScript = 1;
        }

        double scriptPenalty = 0;
        for (var tag : scriptTags) {
            String srcTag = tag.attr("src");
            if (srcTag == null) {
                scriptPenalty += 1;
            }
            else if (srcTag.contains("wp-content") || srcTag.contains("wp-includes") || srcTag.contains("jquery")) {
                scriptPenalty += 0.49;
            }
            else {
                scriptPenalty += 1;
            }

        }
        return (int)(scriptPenalty + badScript + (scriptText.length())/1000.);
    }

    private Map<EdgeUrl, Set<String>> extractLinkWords(EdgePageWordSet keywords, EdgeUrl pageUrl, Document parsed) {


        List<Pair<EdgeUrl, String>> urls = new ArrayList<>();

        Set<String> linkKeywords = new HashSet<>();

        Map<EdgeUrl, Set<String>> linkTextWords = new ConcurrentHashMap<>();

        for (var tag : parsed.getElementsByTag("a")) {
            if (!tag.hasAttr("href")) {
                continue;
            }
            if (urls.size() > 100) {
                break;
            }

            var linkOpt = linkParser.parseLink(pageUrl, tag);
            if (linkOpt.isEmpty())
                continue;

            var link = linkOpt.get();

            urls.add(Pair.of(link, tag.text()));

            if (!Objects.equals(link.domain.domain, pageUrl.domain.domain)
                && linkKeywords.size() <= 25)
            {
                linkKeywords.add("links:" + link.domain.domain);
            }

            Set<String> words = new HashSet<>();

            for (var sent : sentenceExtractor.extractSentencesFromString(tag.text())) {
                for (var keyword : keywordExtractor.getWordsFromSentence(sent)) {
                    words.add(sent.constructWordFromSpan(keyword));
                }
            }

            linkTextWords.compute(link, (k, set) -> {
                if (set == null) return words;
                else { set.addAll(words); return set; }
            });

        }

        keywords.get(IndexBlock.Meta).addAll(linkKeywords);

        if (WordPatterns.wordQualitiesPredicate.test(pageUrl.domain.domain.toLowerCase())) {
            keywords.get(IndexBlock.Link).addJust(pageUrl.domain.domain.toLowerCase());
        }

        return linkTextWords;
    }

    public Optional<String> extractSummary(Document parsed, Set<String> keywords) {
        var cleanDoc = parsed.clone();
        cleanDoc.getElementsByTag("nav").remove();
        cleanDoc.getElementsByTag("header").remove();
        Optional.ofNullable(cleanDoc.getElementById("header")).ifPresent(Element::remove);
        cleanDoc.getElementsByClass("header").remove();
        cleanDoc.getElementsByClass("nav").remove();

        return extractSummaryRaw(cleanDoc)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(s -> !s.isBlank() && s.length() > 20)
                .map(s -> s.substring(0, Math.min(500, s.length())))
                ;
    }

    private Optional<String> extractSummaryRaw(Document parsed) {
        StringBuilder content = new StringBuilder();

        parsed.getElementsByTag("p").forEach(
                elem -> {
                    if (elem.text().length() > elem.html().length()/2) {
                        content.append(elem.text());
                    }
                }
        );

        if (content.length() > 10) {
            return Optional.of(content.toString());
        }
        return Optional.empty();
    }

    private Optional<String> getMetaDescription(Document parsed) {
        return Optional.ofNullable(parsed.select("meta[name=description]")).map(tag -> tag.attr("content")).filter(s -> !s.isBlank());
    }
    private Optional<String> getOgDescription(Document parsed) {
        return Optional.ofNullable(parsed.select("meta[name=og:description]")).map(tag -> tag.attr("content")).filter(s -> !s.isBlank());
    }

}
