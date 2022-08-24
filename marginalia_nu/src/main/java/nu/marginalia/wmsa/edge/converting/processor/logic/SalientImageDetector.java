package nu.marginalia.wmsa.edge.converting.processor.logic;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.HashMap;
import java.util.Map;

public class SalientImageDetector {

    public boolean hasSalientImage(Document document) {
        document.getElementsByTag("a").removeIf(Element::hasText);

        Map<String, Integer> counts = new HashMap<>();
        for (var elem : document.getElementsByTag("img")) {
            counts.merge(elem.attr("src"), 1, Integer::sum);
        }
        for (var elem : document.select("p,div,section,article,font,center")) {

            String tagName = elem.tagName();
            if (("p".equals(tagName) || "center".equals(tagName) || "font".equals(tagName))
                    && elem.text().length() < 16)
            {
                continue;
            }

            if (aTagDensity(elem) < 0.1 && htmlTagDensity(elem) > 0.85) {
                for (var imgTag : elem.getElementsByTag("img")) {
                    if (counts.getOrDefault(imgTag.attr("src"), 1) > 1) {
                        continue;
                    }

                    if (isSmall(imgTag)) {
                        if (!imgTag.id().isBlank()) {
                            continue;
                        }
                    }

                    return true;
                }
            }
        }

        return false;

    }

    private boolean isSmall(Element imgTag) {
        final String width = imgTag.attr("width");
        final String height = imgTag.attr("height");

        if (width.isBlank() || height.isBlank())
            return true;

        try {
            if (Integer.parseInt(width) < 400)
                return true;
            if (Integer.parseInt(height) < 400)
                return true;
        }
        catch (NumberFormatException ex) { /* no-op */ }

        return false;
    }

    private double htmlTagDensity(Element elem) {
        return (double) elem.text().length() / elem.html().length();
    }

    private double aTagDensity(Element elem) {
        return (double) elem.getElementsByTag("a").text().length() / elem.text().length();
    }

}
