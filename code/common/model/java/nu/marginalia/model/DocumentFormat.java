package nu.marginalia.model;

public enum DocumentFormat {
    PLAIN(0, 1, "text"),
    PDF(0, 1, "pdf"),
    UNKNOWN(0, 1, "???"),
    HTML123(0, 1, "html"),
    HTML4(-0.1, 1.05, "html"),
    XHTML(-0.1, 1.05, "html"),
    HTML5(0.5, 1.1, "html");

    /** Used to tune quality score */
    public final double offset;
    /** Used to tune quality score */
    public final double scale;
    public final String shortFormat;

    DocumentFormat(double offset, double scale, String shortFormat) {
        this.offset = offset;
        this.scale = scale;
        this.shortFormat = shortFormat;
    }

}
