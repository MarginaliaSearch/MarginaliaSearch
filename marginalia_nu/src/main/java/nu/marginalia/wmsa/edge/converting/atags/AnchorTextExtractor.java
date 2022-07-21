package nu.marginalia.wmsa.edge.converting.atags;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.SneakyThrows;
import nu.marginalia.util.DenseBitMap;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class AnchorTextExtractor {
    private final Predicate<String> includeDomainPredicate;
    private final Predicate<EdgeUrl> includeUrlPredicate;
    private final BiConsumer<EdgeUrl, String> linkKeywordConsumer;

    private final LinkParser linkParser = new LinkParser();

    private final HashFunction hashFunction = Hashing.murmur3_128();

    // This bit map is used as a bloom filter to deduplicate url-keyword combinations
    // false positives are expected, but that's an acceptable trade-off to not have to deal with
    // de-duplicating billions of shuffled (url, word) tuples on limited hardware
    private final DenseBitMap deduplicateHashBitset = new DenseBitMap(DenseBitMap.MAX_CAPACITY_2GB_16BN_ITEMS);

    public AnchorTextExtractor(Predicate<String> includeDomainPredicate,
                               Predicate<EdgeUrl> includeUrlPredicate,
                               BiConsumer<EdgeUrl, String> linkKeywordConsumer) {
        this.includeDomainPredicate = includeDomainPredicate;
        this.includeUrlPredicate = includeUrlPredicate;
        this.linkKeywordConsumer = linkKeywordConsumer;
    }

    @SneakyThrows
    public void processDocument(String docUrl, String documentBody) {
        final Document processed = Jsoup.parse(documentBody);
        final EdgeUrl documentUrl = new EdgeUrl(docUrl);

        for (var link : processed.getElementsByTag("a")) {
            if (link.hasAttr("href")) {
                String href = link.attr("href");
                String text = getLinkText(link);

                processAnchor(documentUrl, href, text);
            }
        }
    }

    private final Pattern anchorTextNoise = Pattern.compile("[ \t\n\"()“”]+");

    private String getLinkText(Element link) {
        String text = link.text();

        if (link.text().isBlank()) {
            for (var img: link.getElementsByTag("img")) {
                if (img.hasAttr("alt")) {
                    text = img.attr("alt");
                    break;
                }
            }
        }

        return anchorTextNoise.matcher(text.toLowerCase()).replaceAll(" ").trim();
    }

    private void processAnchor(EdgeUrl documentUrl, String href, String text) {
        if (!isInterestingAnchorText(text)) {
            return;
        }

        var optLinkUrl = linkParser.parseLink(documentUrl, href);
        if (optLinkUrl.isEmpty()) return;

        var linkUrl = optLinkUrl.get();

        if (!isInterestingAnchorLink(linkUrl)) {
            return;
        }

        for (String word: anchorTextNoise.split(text)) {
            if (WordPatterns.isStopWord(word))
                continue;

            word = word.toLowerCase();
            if (!WordPatterns.filter(word)) {
                continue;
            }

            if (linkUrl.domain.equals(documentUrl.domain)) {
                continue;
            }

            if (isNewKeywordForLink(word, linkUrl.toString())) {
                linkKeywordConsumer.accept(linkUrl, word);
            }
        }
    }

    // This pattern doesn't need to perfectly capture all anchor texts that are URLs, if it gets 95% that's fine
    private final Predicate<String> looksLikeAnURL = Pattern.compile("(\\p{Alpha}+://)?[\\p{Alnum}.]+(/[^/]+)+").asMatchPredicate();

    private boolean isInterestingAnchorText(String text) {
        if (text.isBlank()) return false;
        if (text.length() > 32) return false;

        // Google loves questions, and so does SEO spammers
        if (text.endsWith("?")) return false;

        if (text.startsWith("http:") || text.startsWith("https:")) return false;

        if (looksLikeAnURL.test(text)) return false;

        return switch (text) {
            case "this", "here", "click", "click here", "download", "source" -> false;
            default -> true;
        };
    }

    private boolean isInterestingAnchorLink(EdgeUrl linkUrl) {
        if (!(linkUrl.proto.endsWith("http") || linkUrl.proto.equals("https"))) {
            return false;
        }

        if (!includeUrlPredicate.test(linkUrl)) {
            return false;
        }

        return includeDomainPredicate.test(linkUrl.domain.toString());
    }

    private boolean isNewKeywordForLink(String href, String text) {
        long hash = 0;

        hash ^= hashFunction.hashString(href, StandardCharsets.UTF_8).padToLong();
        hash ^= hashFunction.hashString(text, StandardCharsets.UTF_8).padToLong();

        // Remove sign bit because we don't want a negative index in deduplicateHashBitset
        hash &= 0x7FFF_FFFF_FFFF_FFFFL;

        return !deduplicateHashBitset.set(hash % deduplicateHashBitset.cardinality);
    }
}
