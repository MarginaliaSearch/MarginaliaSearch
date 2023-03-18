package nu.marginalia.summary.heuristic;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

public class FallbackHeuristic implements SummaryHeuristic {

    @Override
    public String summarize(Document doc) {
        doc = doc.clone();

        int bodyTextLength = doc.body().text().length();

        doc.getElementsByTag("a").remove();

        for (var elem : doc.select("p,div,section,article,font,center,td,h1,h2,h3,h4,h5,h6,tr,th")) {
            if (elem.text().length() < bodyTextLength / 2 && aTagDensity(elem) > 0.25) {
                elem.remove();
            }
        }

        return doc.body().text();
    }

    private double aTagDensity(Element elem) {
        return (double) elem.getElementsByTag("a").text().length() / elem.text().length();
    }
}
