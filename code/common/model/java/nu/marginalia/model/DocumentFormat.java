package nu.marginalia.model;

// This class really doesn't belong anywhere, but will squat here for now
public enum DocumentFormat {
    PLAIN(0, 1),
    PDF(0, 1),
    UNKNOWN(0, 1),
    HTML123(0, 1),
    HTML4(-0.1, 1.05),
    XHTML(-0.1, 1.05),
    HTML5(0.5, 1.1);

    /** Used to tune quality score */
    public final double offset;
    /** Used to tune quality score */
    public final double scale;

    DocumentFormat(double offset, double scale) {
        this.offset = offset;
        this.scale = scale;
    }

}
