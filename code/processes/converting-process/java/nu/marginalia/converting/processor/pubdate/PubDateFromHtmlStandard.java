package nu.marginalia.converting.processor.pubdate;

import nu.marginalia.model.html.HtmlStandard;

public class PubDateFromHtmlStandard {
    /** Used to bias pub date heuristics */
    public static int blindGuess(HtmlStandard standard) {
        return switch (standard) {
            case PLAIN -> 1993;
            case PDF -> 2010;
            case HTML123 -> 1997;
            case HTML4, XHTML -> 2006;
            case HTML5 -> 2018;
            case UNKNOWN -> 2000;
        };
    }

    /** Sanity check a publication year based on the HTML standard.
     * It is for example unlikely for a HTML5 document to be published
     * in 1998, since that is 6 years before the HTML5 standard was published.
     * <p>
     * Discovering publication year involves a lot of guesswork, this helps
     * keep the guesses relatively sane.
     */
    public static boolean isGuessPlausible(HtmlStandard standard, int year) {
        switch (standard) {
            case HTML123:
                return year <= 2000;
            case XHTML:
            case HTML4:
                return year >= 2000;
            case HTML5:
                return year >= 2014;
            default:
                return true;
        }
    }

}
