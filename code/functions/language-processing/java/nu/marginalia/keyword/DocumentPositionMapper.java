package nu.marginalia.keyword;

import nu.marginalia.keyword.model.DocumentKeywordsBuilder;
import nu.marginalia.language.model.DocumentLanguageData;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.model.LanguageDefinition;
import nu.marginalia.language.model.WordRep;
import nu.marginalia.language.pos.PosPatternCategory;
import nu.marginalia.language.sentence.tag.HtmlTag;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.min;
import static java.lang.Math.sqrt;

/** DocumentPositionMapper is responsible for assigning keywords positions in the document,
 * as well as recording spans of positions
 */
public class DocumentPositionMapper {

    public void mapPositionsAndExtractSimpleKeywords(DocumentKeywordsBuilder wordsBuilder,
                                                     KeywordMetadata metadata,
                                                     DocumentLanguageData dld,
                                                     LinkTexts linkTexts)
    {

        // First map the words in the documnent to their positions
        int pos = mapDocumentPositions(wordsBuilder, metadata, dld);

        // Next create some padding space to avoid cross-matching
        pos += 2;

        // Finally allocate some virtual space after the end of the document
        // for the link texts, so that we can match against them as well, although
        // these will be given a different span type.
        mapLinkTextPositions(pos, wordsBuilder, metadata, linkTexts);
    }


    public int mapDocumentPositions(DocumentKeywordsBuilder wordsBuilder,
                                    KeywordMetadata metadata,
                                    DocumentLanguageData dld)

    {

        LanguageDefinition languageDefinition = dld.language();

        List<SpanRecorder> spanRecorders = new ArrayList<>();
        for (var htmlTag : HtmlTag.includedTags) {
            if (!htmlTag.exclude) {
                spanRecorders.add(new SpanRecorder(htmlTag));
            }
        }

        // we use 1-based indexing since the data
        // will be gamma encoded, and it can't represent 0;
        // but the loop starts by incrementing the position,
        // so while unintuitive, zero is correct here.
        int pos = 0;

        for (DocumentSentence sent : dld) {
            for (var word : sent) {
                pos++;

                // Update span position tracking
                for (var recorder : spanRecorders) {
                    recorder.update(sent, pos);
                }

                if (word.isStopWord()) {
                    continue;
                }

                String w = word.wordLowerCase();
                if (matchesWordPattern(w)) {
                    /* Add information about term positions */
                    wordsBuilder.addPos(w, pos);

                    /* Add metadata for word */
                    wordsBuilder.addMeta(w, metadata.getMetadataForWord(word.stemmed()));
                }
            }

            for (var names : languageDefinition.matchGrammarPattern(sent, PosPatternCategory.NAME)) {
                WordRep rep = new WordRep(sent, names);
                byte meta = metadata.getMetadataForWord(rep.stemmed);

                wordsBuilder.addMeta(rep.word, meta);
            }
        }

        pos++; // we need to add one more position to account for the last word in the document

        for (var recorder : spanRecorders) {
            wordsBuilder.addSpans(recorder.finish(pos));
        }

        return pos;
    }

    void mapLinkTextPositions(int startPos,
                              DocumentKeywordsBuilder wordsBuilder,
                              KeywordMetadata metadata,
                              LinkTexts linkTexts)
    {
        int pos = startPos;

        SpanRecorder extLinkRecorder = new SpanRecorder(HtmlTag.EXTERNAL_LINKTEXT);

        LinkTexts.Iter iter = linkTexts.iterator();

        while (iter.next()) {

            DocumentSentence sentence = iter.sentence();
            int count = iter.count();

            // We repeat a link sentence a number of times that is a function of how many times it's been spotted
            // as a link text.  A really "big" link typically has hundreds, if not thousands of repetitions, so we
            // attenuate that a bit with math so we don't generate a needlessly large positions list

            final int repetitions = (int) Math.max(1, min(sqrt(count), 12));

            for (int ci = 0; ci < repetitions; ci++) {

                for (var word : sentence) {
                    pos++;

                    extLinkRecorder.update(sentence, pos);

                    if (word.isStopWord()) {
                        continue;
                    }

                    String w = word.wordLowerCase();
                    if (matchesWordPattern(w)) {
                        /* Add information about term positions */
                        wordsBuilder.addPos(w, pos);

                        /* Add metadata for word */
                        wordsBuilder.addMeta(w, metadata.getMetadataForWord(word.stemmed()));
                    }
                }

                // Add a break between sentences, to prevent them being registered as one long run-on sentence
                extLinkRecorder.endCurrentSpan(pos + 1);

                // Also add some positional padding between separate link texts so we don't match across their boundaries
                pos += 2;
            }
        }

        wordsBuilder.addSpans(extLinkRecorder.finish(pos));
    }

    boolean matchesWordPattern(String s) {
        if (s.length() > 48)
            return false;

        // this function is an unrolled version of the regexp [\da-zA-Z]{1,15}([.\-_/:+*][\da-zA-Z]{1,10}){0,8}

        String wordPartSeparator = ".-_/:+*";

        int i = 0;

        for (int run = 0; run < 15 && i < s.length(); run++) {
            int cp = s.codePointAt(i);


            if (Character.isAlphabetic(cp) || Character.isDigit(cp)) {
                i += Character.charCount(cp);
                continue;
            }

            break;
        }

        if (i == 0)
            return false;

        for (int j = 0; j < 8; j++) {
            if (i == s.length()) return true;

            if (wordPartSeparator.indexOf(s.codePointAt(i)) < 0) {
                return false;
            }

            i++;

            for (int run = 0; run < 10 && i < s.length(); run++) {
                int cp = s.codePointAt(i);

                if (Character.isAlphabetic(cp) || Character.isDigit(cp)) {
                    i += Character.charCount(cp);
                    continue;
                }

                break;
            }
        }

        return false;
    }

}
