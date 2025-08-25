package nu.marginalia.keyword;

import nu.marginalia.keyword.model.DocumentWordSpan;
import nu.marginalia.language.model.DocumentSentence;
import nu.marginalia.language.sentence.tag.HtmlTag;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to record spans of words
 */
class SpanRecorder {
    private final List<DocumentWordSpan> spans = new ArrayList<>();
    private final HtmlTag htmlTag;
    private int start = 0;

    public SpanRecorder(HtmlTag htmlTag) {
        this.htmlTag = htmlTag;
    }

    public void update(DocumentSentence sentence, int pos) {
        assert pos > 0;

        if (sentence.htmlTags.contains(htmlTag)) {
            if (start <= 0) start = pos;
        } else if (sentence.htmlTags.isEmpty() && htmlTag == HtmlTag.BODY) {
            // special case for body tag, we match against no tag on the sentence
            if (start <= 0) start = pos;
        } else {
            if (start > 0) {
                spans.add(new DocumentWordSpan(htmlTag, start, pos));
                start = 0;
            }
        }
    }

    public void endCurrentSpan(int pos) {
        if (start > 0) {
            spans.add(new DocumentWordSpan(htmlTag, start, pos));
            start = 0;
        }
    }

    public List<DocumentWordSpan> finish(int length) {
        if (start > 0) {
            spans.add(new DocumentWordSpan(htmlTag, start, length));
            start = 0;
        }
        return spans;
    }
}
