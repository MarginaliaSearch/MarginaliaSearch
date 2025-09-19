package nu.marginalia.language.model;

import nu.marginalia.language.sentence.SentenceExtractor;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.lsh.EasyLSH;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/** Holds the sentences and text of a document, decorated with
 * HTML tags, POS tags, and other information.
 *
 * @see SentenceExtractor
 */
public record DocumentLanguageData(LanguageDefinition language,
                                   List<DocumentSentence> sentences,
                                   String text) implements Iterable<DocumentSentence> {

    public DocumentLanguageData(LanguageDefinition language, List<DocumentSentence> sentences, String text)
    {
        this.language = language;
        this.sentences = Collections.unmodifiableList(sentences);
        this.text = text;
    }

    public List<DocumentSentence> findSentencesForTag(HtmlTag tag) {
        return stream().filter(s -> s.htmlTags.contains(tag)).toList();
    }

    public int numSentences() {
        return sentences.size();
    }

    public int totalNumWords() {
        int ret = 0;

        for (DocumentSentence sent : sentences) {
            ret += sent.length();
        }

        return ret;
    }

    public long localitySensitiveHashCode() {
        var hash = new EasyLSH();

        for (var sent : sentences) {
            for (var word : sent.wordsLowerCase) {
                hash.addUnordered(word);
            }
        }
        return hash.get();
    }

    @NotNull
    @Override
    public Iterator<DocumentSentence> iterator() {
        return sentences.iterator();
    }

    public Stream<DocumentSentence> stream() {
        return sentences.stream();
    }
}
