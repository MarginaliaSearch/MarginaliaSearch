package nu.marginalia.wmsa.edge.model.crawl;

public enum EdgeHtmlStandard {
    PLAIN(0, 1),
    UNKNOWN(0, 1),
    HTML123(0, 1),
    HTML4(-0.1, 1.05),
    XHTML(-0.1, 1.05),
    HTML5(0.5, 1.1);

    public final double offset;
    public final double scale;

    EdgeHtmlStandard(double offset, double scale) {
        this.offset = offset;
        this.scale = scale;
    }

}
