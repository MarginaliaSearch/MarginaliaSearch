package nu.marginalia.converting.model;


public enum HtmlStandard {
    PLAIN(0, 1),
    UNKNOWN(0, 1),
    HTML123(0, 1),
    HTML4(-0.1, 1.05),
    XHTML(-0.1, 1.05),
    HTML5(0.5, 1.1);

    /** Used to tune quality score */
    public final double offset;
    /** Used to tune quality score */
    public final double scale;

    HtmlStandard(double offset, double scale) {
        this.offset = offset;
        this.scale = scale;
    }

}
