package nu.marginalia.wmsa.edge.converting.atags;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.SneakyThrows;
import nu.marginalia.util.DenseBitMap;
import nu.marginalia.util.language.WordPatterns;
import nu.marginalia.wmsa.configuration.WmsaHome;
import nu.marginalia.wmsa.edge.assistant.dict.NGramBloomFilter;
import nu.marginalia.wmsa.edge.assistant.dict.TermFrequencyDict;
import nu.marginalia.wmsa.edge.converting.processor.logic.LinkParser;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.apache.logging.log4j.util.Strings;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
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

    private final NGramBloomFilter nGramBloomFilter;
    private final TermFrequencyDict termFrequencyDict;

    public AnchorTextExtractor(Predicate<String> includeDomainPredicate,
                               Predicate<EdgeUrl> includeUrlPredicate,
                               BiConsumer<EdgeUrl, String> linkKeywordConsumer) throws IOException {
        this.includeDomainPredicate = includeDomainPredicate;
        this.includeUrlPredicate = includeUrlPredicate;
        this.linkKeywordConsumer = linkKeywordConsumer;

        nGramBloomFilter = new NGramBloomFilter(WmsaHome.getLanguageModels());
        termFrequencyDict = new TermFrequencyDict(WmsaHome.getLanguageModels());
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

    Set<String> excludedTerminators = Set.of("a", "for", "of", "in", "with", "but", "as", "by", "on", "to", "at", "-");

    private void processAnchor(EdgeUrl documentUrl, String href, String text) {
        text = trimText(text);

        if (!isInterestingAnchorText(text)) {
            return;
        }

        var optLinkUrl = linkParser.parseLink(documentUrl, href);
        if (optLinkUrl.isEmpty()) return;

        var linkUrl = optLinkUrl.get();

        if (!isInterestingAnchorLink(linkUrl)) {
            return;
        }

        if (Objects.equals(domainHash(linkUrl), domainHash(documentUrl))) {
            return;
        }

        String[] wordParts = anchorTextNoise.split(text.toLowerCase());

        if (wordParts.length > 1) {
            String word = Strings.join(Arrays.asList(wordParts), '_');

            addKeywordIfExistsInTermFreqDictionary(linkUrl, word);

            if (word.contains(".")) {
                addKeywordIfExistsInTermFreqDictionary(linkUrl, removePeriods(word));
            }

            if (wordParts.length > 2) {
                for (int i = 1; i < wordParts.length; i++) {
                    if (excludedTerminators.contains(wordParts[i])) continue;
                    if (excludedTerminators.contains(wordParts[i-1])) continue;

                    word = wordParts[i-1] + "_" + wordParts[i];
                    addKeywordIfExistsInTermFreqDictionary(linkUrl, word);

                    if (word.contains(".")) {
                        addKeywordIfExistsInTermFreqDictionary(linkUrl, removePeriods(word));
                    }
                }
            }

            if (wordParts.length > 3) {
                for (int i = 2; i < wordParts.length; i++) {
                    if (excludedTerminators.contains(wordParts[i])) continue;
                    if (excludedTerminators.contains(wordParts[i-2])) continue;

                    word = wordParts[i-2] + "_" + wordParts[i-1] + "_" + wordParts[i];

                    addKeywordIfExistsInTermFreqDictionary(linkUrl, word);

                    if (word.contains(".")) {
                        word = removePeriods(word);
                        addKeywordIfExistsInTermFreqDictionary(linkUrl, removePeriods(word));
                    }
                }
            }

        }

        for (String word: wordParts) {
            if (!WordPatterns.isStopWord(word)
                && WordPatterns.filter(word)
                && isNewKeywordForLink(word, linkUrl.toString())
            ) {
                linkKeywordConsumer.accept(linkUrl, word);
            }
        }

        for (String word: wordParts) {
            if (word.length() > 2 && word.endsWith("'s")) {
                word = word.substring(0, word.length()-2);
            }

            if (!WordPatterns.isStopWord(word)
                    && WordPatterns.filter(word)
                    && isNewKeywordForLink(word, linkUrl.toString())
            ) {
                linkKeywordConsumer.accept(linkUrl, word);
            }
        }
    }

    private void addKeywordIfExistsInTermFreqDictionary(EdgeUrl linkUrl, String word) {
        if (termFrequencyDict.getTermFreq(word) > 0 || nGramBloomFilter.isKnownNGram(word)) {
            if (isNewKeywordForLink(word, linkUrl.toString())) {
                linkKeywordConsumer.accept(linkUrl, word);
            }
        }
    }

    Pattern p = Pattern.compile("\\.");
    private String removePeriods(String s) {
        return p.matcher(s).replaceAll("");
    }

    private String domainHash(EdgeUrl url) {
        var domain = url.domain;
        if ("www".equals(domain.subDomain)) {
            return domain.domain;
        }
        return domain.toString();
    }

    private String trimText(String text) {
        int start = text.length()-1;
        int end = 0;

        for (int i = text.length(); i > 0; i--) {
            if (Character.isLetterOrDigit(text.charAt(i-1))) {
                end = i;
                break;
            }
        }

        for (int i = 0; i < end; i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                start = i;
                break;
            }
        }

        if (start >= 0 && start < end) {
            return text.substring(start, end);
        }

        return "";
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

    private synchronized boolean isNewKeywordForLink(String href, String text) {
        long hash = 0;

        hash ^= hashFunction.hashString(href, StandardCharsets.UTF_8).padToLong();
        hash ^= hashFunction.hashString(text, StandardCharsets.UTF_8).padToLong();

        // Remove sign bit because we don't want a negative index in deduplicateHashBitset
        hash &= 0x7FFF_FFFF_FFFF_FFFFL;

        return !deduplicateHashBitset.set(hash % deduplicateHashBitset.cardinality);
    }
}
