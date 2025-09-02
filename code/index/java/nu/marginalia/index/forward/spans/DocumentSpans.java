package nu.marginalia.index.forward.spans;

import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.sequence.CodedSequence;

/** All spans associated with a document
 * <p></p>
 * A span is a list of document positions that are associated with a particular tag in the document.
 * */
public class DocumentSpans {
    private static final DocumentSpan EMPTY_SPAN = new DocumentSpan();

    public DocumentSpan title = EMPTY_SPAN;
    public DocumentSpan heading = EMPTY_SPAN;
    public DocumentSpan body = EMPTY_SPAN;

    public DocumentSpan nav = EMPTY_SPAN;
    public DocumentSpan code = EMPTY_SPAN;
    public DocumentSpan anchor = EMPTY_SPAN;

    public DocumentSpan externalLinkText = EMPTY_SPAN;

    public DocumentSpan getSpan(HtmlTag tag) {
        if (tag == HtmlTag.HEADING)
            return heading;
        else if (tag == HtmlTag.TITLE)
            return title;
        else if (tag == HtmlTag.NAV)
            return nav;
        else if (tag == HtmlTag.CODE)
            return code;
        else if (tag == HtmlTag.ANCHOR)
            return anchor;
        else if (tag == HtmlTag.EXTERNAL_LINKTEXT)
            return externalLinkText;
        else if (tag == HtmlTag.BODY)
            return body;

        return EMPTY_SPAN;
    }

    void accept(byte code, IntList positions) {
        if (code == HtmlTag.HEADING.code)
            this.heading = new DocumentSpan(positions);
        else if (code == HtmlTag.TITLE.code)
            this.title = new DocumentSpan(positions);
        else if (code == HtmlTag.NAV.code)
            this.nav = new DocumentSpan(positions);
        else if (code == HtmlTag.CODE.code)
            this.code = new DocumentSpan(positions);
        else if (code == HtmlTag.ANCHOR.code)
            this.anchor = new DocumentSpan(positions);
        else if (code == HtmlTag.EXTERNAL_LINKTEXT.code)
            this.externalLinkText = new DocumentSpan(positions);
        else if (code == HtmlTag.BODY.code)
            this.body = new DocumentSpan(positions);
    }

    void accept(byte code, CodedSequence positions) {
        if (code == HtmlTag.HEADING.code)
            this.heading = new DocumentSpan(positions);
        else if (code == HtmlTag.TITLE.code)
            this.title = new DocumentSpan(positions);
        else if (code == HtmlTag.NAV.code)
            this.nav = new DocumentSpan(positions);
        else if (code == HtmlTag.CODE.code)
            this.code = new DocumentSpan(positions);
        else if (code == HtmlTag.ANCHOR.code)
            this.anchor = new DocumentSpan(positions);
        else if (code == HtmlTag.EXTERNAL_LINKTEXT.code)
            this.externalLinkText = new DocumentSpan(positions);
        else if (code == HtmlTag.BODY.code)
            this.body = new DocumentSpan(positions);
    }

}
