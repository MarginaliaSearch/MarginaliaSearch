package nu.marginalia.atags;

import com.google.inject.Inject;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.model.Link;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.model.EdgeUrl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class AnchorTextKeywords {
    private final SentenceExtractor sentenceExtractor;
    private final Set<String> stopList;

    @Inject
    public AnchorTextKeywords(SentenceExtractor sentenceExtractor)
    {
        this.sentenceExtractor = sentenceExtractor;

        stopList = readStoplist();
    }

    private Set<String> readStoplist() {
        Set<String> ret = new HashSet<>();

        try (var resource = Objects.requireNonNull(ClassLoader.getSystemResourceAsStream("atags-stop-list"),
                "Could not load anchor tags stop list");
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

    public LinkTexts getAnchorTextKeywords(DomainLinks links, EdgeUrl url) {
        List<Link> keywordsRaw = links.forUrl(url);

        List<DocumentSentence> ret = new ArrayList<>(keywordsRaw.size());

        // Extract and count keywords from anchor text
        for (Link keyword : keywordsRaw) {
            if (stopList.contains(keyword.text().toLowerCase()))
                continue;

            var sentence = sentenceExtractor.extractSentence(keyword.text(), EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
            ret.add(sentence);
        }

        return new LinkTexts(ret);
    }

    public LinkTexts getAnchorTextKeywords(DomainLinks links, List<EdgeUrl> urls) {
        List<Link> keywordsRaw = new ArrayList<>();
        for (var url : urls) {
            links.forUrl(url);
        }

        List<DocumentSentence> ret = new ArrayList<>(keywordsRaw.size());

        // Extract and count keywords from anchor text
        for (Link keyword : keywordsRaw) {
            if (stopList.contains(keyword.text().toLowerCase()))
                continue;

            var sentence = sentenceExtractor.extractSentence(keyword.text(), EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
            ret.add(sentence);
        }

        return new LinkTexts(ret);
    }
}
