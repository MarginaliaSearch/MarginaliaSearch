package nu.marginalia.atags;

import com.google.inject.Inject;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.keyword.KeywordExtractor;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.model.EdgeUrl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class AnchorTextKeywords {
    private final KeywordExtractor keywordExtractor;
    private final SentenceExtractor sentenceExtractor;
    private final Set<String> stopList;
    @Inject
    public AnchorTextKeywords(KeywordExtractor keywordExtractor,
                              SentenceExtractor sentenceExtractor)
    {
        this.keywordExtractor = keywordExtractor;
        this.sentenceExtractor = sentenceExtractor;

        stopList = readStoplist();
    }

    private Set<String> readStoplist() {
        Set<String> ret = new HashSet<>();

        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("atags-stop-list"),
                "Could not load word frequency table");
             var br = new BufferedReader(new InputStreamReader(resource))
        ) {
            while (true) {
                String s = br.readLine();

                if (s == null) break;
                if (s.isBlank()) continue;

                ret.add(s.trim());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public List<String> getAnchorTextKeywords(DomainLinks links, EdgeUrl url) {
        var keywordsRaw = links.forUrl(url);

        // Extract and count keywords from anchor text
        Map<String, Integer> wordsWithCount = new HashMap<>();
        for (var keyword : keywordsRaw) {
            if (stopList.contains(keyword.text().toLowerCase()))
                continue;

            var sentence = sentenceExtractor.extractSentence(keyword.text());
            for (var wordSpan : keywordExtractor.getKeywordsFromSentence(sentence)) {
                wordsWithCount.merge(sentence.constructWordFromSpan(wordSpan), 1, Integer::sum);
            }
        }

        // Filter out keywords that appear infrequently
        final List<String> keywords = new ArrayList<>(wordsWithCount.size());
        for (var wordEntry : wordsWithCount.entrySet()) {
            if (wordEntry.getValue() > 2) {
                keywords.add(wordEntry.getKey());
            }
        }

        return keywords;
    }
}
