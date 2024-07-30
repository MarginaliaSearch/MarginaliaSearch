package nu.marginalia.index.forward.spans;

import nu.marginalia.language.sentence.tag.HtmlTag;
import nu.marginalia.sequence.CodedSequence;

public class DocumentSpans {
    private static DocumentSpan EMPTY_SPAN = new DocumentSpan();

    public DocumentSpan title = EMPTY_SPAN;
    public DocumentSpan heading = EMPTY_SPAN;

    public DocumentSpan nav = EMPTY_SPAN;
    public DocumentSpan pageHeader = EMPTY_SPAN;
    public DocumentSpan pageFooter = EMPTY_SPAN;
    public DocumentSpan code = EMPTY_SPAN;
    public DocumentSpan pre = EMPTY_SPAN;

    void accept(byte code, CodedSequence positions) {
        if (code == HtmlTag.HEADING.code)
            this.heading = new DocumentSpan(positions);
        else if (code == HtmlTag.TITLE.code)
            this.title = new DocumentSpan(positions);
        else if (code == HtmlTag.NAV.code)
            this.nav = new DocumentSpan(positions);
        else if (code == HtmlTag.PAGE_HEADER.code)
            this.pageHeader = new DocumentSpan(positions);
        else if (code == HtmlTag.PAGE_FOOTER.code)
            this.pageFooter = new DocumentSpan(positions);
        else if (code == HtmlTag.CODE.code)
            this.code = new DocumentSpan(positions);
        else if (code == HtmlTag.PRE.code)
            this.pre = new DocumentSpan(positions);
    }

}
