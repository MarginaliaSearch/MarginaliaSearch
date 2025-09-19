package nu.marginalia.atags;

import com.google.inject.Inject;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import nu.marginalia.atags.model.DomainLinks;
import nu.marginalia.atags.model.Link;
import nu.marginalia.keyword.LinkTexts;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.model.EdgeUrl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class AnchorTextKeywords {
    private final SentenceExtractor sentenceExtractor;
    private final LanguageDefinition englishLanguage;
    private final Set<String> stopList;

    @Inject
    public AnchorTextKeywords(SentenceExtractor sentenceExtractor, LanguageConfiguration languageConfiguration)
    {
        this.sentenceExtractor = sentenceExtractor;

        // FIXME:  Currently the atags file does not provide information about the language in the source document
        //         which means we have to run the link texts through English processing.  For euro-languages this is
        //         likely fine, but for stuff like Japanese it's going to produce bad results.  We'll need to add this
        //         information when extracting link texts so we can use the appropriate language processing here later.
        //         (sampling based on the link text alone is likely insufficient, since the sample size is going to be 2-3 words).
        this.englishLanguage = languageConfiguration.getLanguage("en");

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
        TIntList counts = new TIntArrayList(keywordsRaw.size());

        // Extract and count keywords from anchor text
        for (Link keyword : keywordsRaw) {
            if (stopList.contains(keyword.text().toLowerCase()))
                continue;

            var sentence = sentenceExtractor.extractSentence(englishLanguage, keyword.text(), EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
            ret.add(sentence);
            counts.add(keyword.count());
        }

        return new LinkTexts(ret, counts);
    }

    public LinkTexts getAnchorTextKeywords(DomainLinks links, List<EdgeUrl> urls) {
        List<Link> keywordsRaw = new ArrayList<>();
        for (var url : urls) {
            keywordsRaw.addAll(links.forUrl(url));
        }

        List<DocumentSentence> ret = new ArrayList<>(keywordsRaw.size());
        TIntList counts = new TIntArrayList(keywordsRaw.size());

        // Extract and count keywords from anchor text
        for (Link keyword : keywordsRaw) {
            if (stopList.contains(keyword.text().toLowerCase()))
                continue;

            var sentence = sentenceExtractor.extractSentence(englishLanguage, keyword.text(), EnumSet.of(HtmlTag.EXTERNAL_LINKTEXT));
            ret.add(sentence);
            counts.add(keyword.count());
        }

        return new LinkTexts(ret, counts);
    }
}
