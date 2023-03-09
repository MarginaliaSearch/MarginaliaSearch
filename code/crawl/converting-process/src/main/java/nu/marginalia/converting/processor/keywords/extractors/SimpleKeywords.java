package nu.marginalia.converting.processor.keywords.extractors;

import nu.marginalia.converting.model.DocumentKeywordsBuilder;
import nu.marginalia.language.WordPatterns;
import nu.marginalia.language.encoding.AsciiFlattener;
import nu.marginalia.language.keywords.KeywordExtractor;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.KeywordMetadata;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.model.crawl.EdgePageWordFlags;

import java.util.EnumSet;

public class SimpleKeywords {
    private final KeywordExtractor keywordExtractor;

    public SimpleKeywords(KeywordExtractor keywordExtractor) {
        this.keywordExtractor = keywordExtractor;
    }

    public void getSimpleWords(DocumentKeywordsBuilder wordsBuilder,
                               KeywordMetadata metadata,
                               DocumentLanguageData documentLanguageData) {

        EnumSet<EdgePageWordFlags> flagsTemplate = EnumSet.noneOf(EdgePageWordFlags.class);

        for (var sent : documentLanguageData.sentences) {

            if (wordsBuilder.size() > 1500)
                break;

            for (var word : sent) {
                if (word.isStopWord()) {
                    continue;
                }

                String w = AsciiFlattener.flattenUnicode(word.wordLowerCase());
                if (WordPatterns.singleWordQualitiesPredicate.test(w)) {
                    wordsBuilder.add(w, metadata.getMetadataForWord(flagsTemplate, word.stemmed()));
                }
            }

            for (var names : keywordExtractor.getProperNames(sent)) {
                var rep = new WordRep(sent, names);
                String w = AsciiFlattener.flattenUnicode(rep.word);

                wordsBuilder.add(w, metadata.getMetadataForWord(flagsTemplate, rep.stemmed));
            }
        }

    }


}
