package nu.marginalia.encyclopedia.cleaner.model;

import org.jsoup.Jsoup;

import java.util.List;

public record ArticleParts(List<String> parts) {
    public ArticleParts(String... parts) {
        this(List.of(parts));
    }
    public String articleHtml() {
        StringBuilder sb = new StringBuilder();
        for (String part : parts()) {
            sb.append(part);
        }
        return sb.toString();
    }

    public String getSummary() {
        if (parts.isEmpty())
            return "";

        String firstPart = parts.get(0);
        var doclet = Jsoup.parse(firstPart);
        doclet.getElementsByTag("b").tagName("span");
        var firstP = doclet.select("p").first();

        if (null == firstP)
            return "";

        StringBuilder ret = new StringBuilder();
        ret.append(firstP.outerHtml());

        var nextSibling = firstP.nextElementSibling();

        if (nextSibling != null &&
                !"p".equals(nextSibling.tagName()) &&
                !"table".equals(nextSibling.tagName()))
        {
            ret.append(" ").append(nextSibling.outerHtml());
        }
        return ret.toString();
    }
}
