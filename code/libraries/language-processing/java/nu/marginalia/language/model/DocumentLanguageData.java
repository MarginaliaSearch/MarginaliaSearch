package nu.marginalia.language.model;

import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.lsh.EasyLSH;

import java.util.Arrays;
import java.util.List;

/** Holds the sentences and text of a document, decorated with
 * HTML tags, POS tags, and other information.
 *
 * @see SentenceExtractor
 */
public class DocumentLanguageData {
    public final DocumentSentence[] sentences;
    public final String text;

    public DocumentLanguageData(List<DocumentSentence> sentences,
                                String text) {
        this.sentences = sentences.toArray(DocumentSentence[]::new);
        this.text = text;
    }

    public List<DocumentSentence> findSentencesForTag(HtmlTag tag) {
        return Arrays.stream(sentences).filter(s -> s.htmlTags.contains(tag)).toList();
    }

    public int totalNumWords() {
        int ret = 0;

        for (int i = 0; i < sentences.length; i++) {
            ret += sentences[i].length();
        }

        return ret;
    }

    public long localitySensitiveHashCode() {
        var hash = new EasyLSH();

        for (var sent : sentences) {
            for (var word : sent) {
                hash.addUnordered(word.word());
            }
        }
        return hash.get();
    }
}
