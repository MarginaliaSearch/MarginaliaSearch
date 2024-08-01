package nu.marginalia.converting.processor.summary.heuristic;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Collection;

public class TagDensityHeuristic implements SummaryHeuristic {
    private final int maxSummaryLength;

    @Inject
    public TagDensityHeuristic(@Named("max-summary-length") Integer maxSummaryLength) {
        this.maxSummaryLength = maxSummaryLength;
    }

    @Override
    public String summarize(Document doc, Collection<String> importantWords) {
        doc = doc.clone();

        StringBuilder content = new StringBuilder();

        for (var elem : doc.select("p,div,section,article,font,center")) {
            if (content.length() >= maxSummaryLength) break;

            String tagName = elem.tagName();
            if (("p".equals(tagName) || "center".equals(tagName) || "font".equals(tagName))
                    && elem.text().length() < 16)
            {
                continue;
            }

            if (aTagDensity(elem) < 0.1 && htmlTagDensity(elem) > 0.85) {
                content.append(elem.text()).append(' ');
            }
        }

        if (content.length() > 32) {
            // AAAABBBBCCCCDDDDEEEEFFFFGGGGHHHH
            return content.toString();
        }

        return "";
    }

    private double htmlTagDensity(Element elem) {
        return (double) elem.text().length() / elem.html().length();
    }

    private double aTagDensity(Element elem) {
        return (double) elem.getElementsByTag("a").text().length() / elem.text().length();
    }

}
