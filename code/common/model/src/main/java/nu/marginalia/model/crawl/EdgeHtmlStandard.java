package nu.marginalia.model.crawl;


public enum EdgeHtmlStandard {
    PLAIN(0, 1, 1993),
    UNKNOWN(0, 1, 2000),
    HTML123(0, 1, 1997),
    HTML4(-0.1, 1.05, 2006),
    XHTML(-0.1, 1.05, 2006),
    HTML5(0.5, 1.1, 2018);

    /** Used to tune quality score */
    public final double offset;
    /** Used to tune quality score */
    public final double scale;

    /** This parameter is used to bias publish date heuristics
     * */
    public final int yearGuess;

    EdgeHtmlStandard(double offset, double scale, int yearGuess) {
        this.offset = offset;
        this.scale = scale;
        this.yearGuess = yearGuess;
    }

}
