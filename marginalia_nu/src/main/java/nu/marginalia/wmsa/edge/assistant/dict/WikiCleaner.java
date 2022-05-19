package nu.marginalia.wmsa.edge.assistant.dict;

import lombok.SneakyThrows;
import net.sourceforge.jeuclid.MathMLParserSupport;
import net.sourceforge.jeuclid.context.Display;
import net.sourceforge.jeuclid.context.LayoutContextImpl;
import net.sourceforge.jeuclid.context.Parameter;
import net.sourceforge.jeuclid.font.FontFactory;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeFilter;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;


public class WikiCleaner {

    static {
        try (var font = ClassLoader.getSystemResourceAsStream("fonts/LM-regular.ttf")) {
            FontFactory.getInstance().registerFont(Font.TRUETYPE_FONT, font);
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
        }
        try (var font = ClassLoader.getSystemResourceAsStream("fonts/STIXTwoMath-Regular.ttf")) {
            FontFactory.getInstance().registerFont(Font.TRUETYPE_FONT, font);
        } catch (IOException | FontFormatException e) {
            e.printStackTrace();
        }
    }
    public String cleanWikiJunk(String url, String html) {
        return cleanWikiJunk(url, Jsoup.parse(html));
    }

    public List<String> extractLinkWords(String data) {
        var doc = Jsoup.parse(data);
        return getWikiPageLinkText(doc);
    }

    public String cleanWikiJunk(String url, Document doc) {

        if (doc.getElementById("content") == null) {
            return null;
        }
        List<Pair<String, String>> disambig = getDisambiguationLinks(doc);
        List<Pair<String, String>> topLinks = getWikiPageLinks(doc);

        removeTag(doc, "script", "object", "embed", "audio", "style", "noscript", "link", "meta", "img");
        doc.getElementsByClass("mwe-math-element").forEach(this::convertMathTag);
        removeByClass(doc, "infobox", "collapsible", "navbar", "printfooter",
                                    "mw-editsection", "thumb", "sidebar", "navbox", "mw-jump-link",
                                    "vertical-navbox");
        removeByClass(doc, "mw-indicators", "noprint", "sistersitebox");
        removeIds(doc, "coordinates", "mw-page-base", "mw-head-base", "site-notice", "contentSub", "contentSub2");

        doc.getElementsByAttributeValue("role", "presentation").remove();

        doc.getElementsByTag("a").forEach(atag -> {
            var href = atag.attr("href");
            var parent = atag.parent();

            if ("li".equals(parent.tagName())) {
                atag.removeAttr("title");
                if (href.startsWith("http://")) {
                    atag.addClass("extern-link");
                    atag.attr("rel", "nofollow");
                    return;
                }
            }
            else {
                atag.replaceWith(new TextNode(atag.text()));
            }
        });

        Optional.ofNullable(doc.getElementsByTag("cite")).ifPresent(cite -> cite.forEach(c -> {
            c.tagName("span");
        }));


        removeIds(doc, "toc", "catlinks", "Notes", "mw-navigation", "mw-data-after-content", "jump-to-nav");
        removeByClass(doc, "mw-references-wrap", "references", "reference", "siteSub", "refbegin");

        // doc.getElementById("mw-content-text").insertChildren(0, doc.getElementById("firstHeading"));
        doc.getElementById("content").tagName("article");
        doc.getAllElements().forEach(elem -> {
            if (elem.parent() != null
                    && "summary".equals(elem.parent().tagName()))
            {
                elem.parent().replaceWith(elem);
            }
        });

        doc.getElementsByTag("span").forEach(elem -> {
            if ("pre".equals(elem.parent().tagName())) {
                if (elem.hasClass("linenos")) {
                    elem.replaceWith(new TextNode(String.format("%-4s", elem.text())));
                }
                else {
                    elem.replaceWith(new TextNode(elem.text()));
                }
            }
            else {
                elem.replaceWith(new TextNode(" " + elem.text() + " "));
            }
        });

        doc.getElementsByTag("details").forEach(deets -> {
            if (deets.children().size() == 1) {
                deets.replaceWith(deets.children().first());
            }
            else {
                deets.tagName("div");
            }
        });

        removeEmptyTags(doc, "li");
        removeEmptyTags(doc, "ul");
        removeEmptyTags(doc, "div");

        doc.getElementsByTag("p").forEach(elem -> {
            if ("blockquote".equals(elem.parent().tagName())) {
                elem.replaceWith(new TextNode(elem.text()));
            }
        });

        removeEmptyTags(doc, "p");

        doc.getElementsByTag("h4").forEach(elem -> {
            var next = elem.nextElementSibling();
            if (next == null) {
                elem.remove();
                return;
            }
            String nextTagName = next.tagName();
            if ("h4".equals(nextTagName) || "h3".equals(nextTagName) || "h2".equals(nextTagName)) {
                elem.remove();
            }
        });


        doc.getElementsByTag("h3").forEach(elem -> {
            var next = elem.nextElementSibling();
            if (next == null) {
                elem.remove();
                return;
            }
            String nextTagName = next.tagName();
            if ("h3".equals(nextTagName) || "h2".equals(nextTagName)) {
                elem.remove();
            }
        });

        doc.getElementsByTag("h2").forEach(elem -> {
            var next = elem.nextElementSibling();
            if (next == null) {
                elem.remove();
                return;
            }
            if ("h2".equals(next.tagName())) {
                elem.remove();
            }
        });
        doc.getElementsByTag("footer").remove();
        doc.getElementsByTag("table").forEach(table -> {
            table.attr("border", "1");
        });
        doc.getElementsByTag("table").forEach(table -> {
            if ("right".equals(table.attr("align"))) {
                table.remove();
            }
        });

        doc.getElementsByTag("head").append("<meta charset=\"UTF-8\">");
        doc.getElementsByTag("head").append("<link rel=\"stylesheet\" href=\"https://www.marginalia.nu/style.css\"/>");
        doc.getElementsByTag("head").append("<link rel=\"stylesheet\" href=\"https://search.marginalia.nu/style.css\"/>");
        doc.getElementsByTag("head").append("<link rel=\"stylesheet\" href=\"https://encyclopedia.marginalia.nu/style.css\"/>");
        doc.getElementsByTag("head").append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        doc.getElementsByTag("head").append("<meta name=\"robots\" content=\"noindex\">");

        if (!topLinks.isEmpty()) {
            doc.getElementsByTag("article").append("<h1>Index of References</h1><ul>" +
                    topLinks.stream().sorted(Comparator.comparing(p -> p.getValue().toUpperCase())).distinct().map(href -> "<li><a href=\""+href.getKey()+"\">"+href.getValue()+"</href>").collect(Collectors.joining("</li>"))
                    + "</ul>");
        }

        if (!disambig.isEmpty()) {
            doc.getElementsByTag("h1").first().nextElementSibling().prepend("<details class=\"margin-note\"><summary>See Also</summary>" +
                    disambig.stream().map(href -> "<a href=\""+href.getKey()+"\">"+href.getValue()+"</href>").collect(Collectors.joining("<br>"))
                    + "</div>");
        }

        doc.getElementsByTag("article").first().parent().prepend("<header><nav><a href=\"/\">Index</a> <a href=\"/wiki-clean.html\">What is this?</a><a href=\""+url+"\" class=\"verb\">On Wikipedia</a></nav></header>");
        doc.getElementsByTag("article").first().parent().append("<footer> Based on an archived copy of<br><a class=\"fancy-teknisk\" rel=\"nofollow\" href=\"" + url + "\">"+url+"</a><br> Text is available under the <a rel=\"nofollow\" href=\"https://search.marginalia.nu/wiki/Wikipedia:Text_of_Creative_Commons_Attribution-ShareAlike_3.0_Unported_License\">Creative Commons Attribution-ShareAlike 3.0 License</a>; additional terms may apply.</footer>");

        doc.getElementsByTag("div").forEach(tag -> {
            if (tag.text().startsWith("This article is issued from Wikipedia")) {
                tag.remove(); // we have our own
            }
        });
        doc.getAllElements().forEach(elem -> {
            var classes = elem.classNames().stream().filter(this::isWikiClass).collect(Collectors.toList());
            classes.forEach(elem::removeClass);
            elem.removeAttr("lang");
            elem.removeAttr("dir");
            elem.removeAttr("id");
            elem.removeAttr("role");
            elem.removeAttr("style");
            elem.removeAttr("tabindex");
            elem.removeAttr("aria-haspopup");
            elem.removeAttr("data-section-id");
            elem.removeAttr("aria-expanded");
            elem.removeAttr("aria-pressed");
            elem.removeAttr("open");
            elem.removeAttr("data-level");
        });

        marginifyHeaders(doc);


        doc.filter(new NodeFilter() {
            @Override
            public FilterResult head(Node node, int depth) {
                if (node instanceof Comment) {
                    return FilterResult.REMOVE;
                }
                return FilterResult.CONTINUE;
            }

            @Override
            public FilterResult tail(Node node, int depth) {
                if (node instanceof Comment) {
                    return FilterResult.REMOVE;
                }
                return FilterResult.CONTINUE;
            }
        });
        return doc.html();
    }

    @SneakyThrows
    private void convertMathTag(Element math) {

        try {
            var formula = math.getElementsByTag("math");
            var converter = net.sourceforge.jeuclid.converter.Converter.getInstance();
            var sos = new ByteArrayOutputStream();
            var alt = Optional.ofNullable(formula.attr("alttext"))
                    .or(() -> Optional.ofNullable(math.getElementsByTag("annotation").text()))
                    .orElse("");

            var layoutContext = new LayoutContextImpl(LayoutContextImpl.getDefaultLayoutContext());

            String parentTag = math.parent().tag().getName();
            boolean topLevel = "dd".equals(parentTag) || "div".equals(parentTag)
                    || (math.nextElementSibling() == null && math.previousElementSibling() == null);

            int mathSize = 16;
            if (topLevel)
                mathSize = 24;
            if ("h1".equals(parentTag)) {
                mathSize = 28;
            }
            if ("h2".equals(parentTag)) {
                mathSize = 24;
            }
            if ("h3".equals(parentTag)) {
                mathSize = 22;
            }
            layoutContext.setParameter(Parameter.MATHSIZE, mathSize);

            layoutContext.setParameter(Parameter.ANTIALIAS, true);
            layoutContext.setParameter(Parameter.SCRIPTMINSIZE, 8);
            layoutContext.setParameter(Parameter.FONTS_SERIF, "STIX Two Math");
            layoutContext.setParameter(Parameter.FONTS_SCRIPT, "STIX Two Math");
            layoutContext.setParameter(Parameter.DISPLAY, topLevel ? Display.BLOCK : Display.INLINE);

            converter.convert(MathMLParserSupport.parseString(
                    formula.html().replace("&nbsp;", " ")), sos,
                    "image/png",
                    layoutContext).toString();

            math.tagName("img")
                    .text("")
                    .attr("src", "data:image/png;base64," + Base64.getEncoder().encodeToString(sos.toByteArray()))
                    .attr("alt", alt);

        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void removeEmptyTags(Document doc, String tag) {
        doc.getElementsByTag(tag).forEach(elem -> {
            if (elem.text().isBlank() && elem.getElementsByTag("img").isEmpty()) {
                elem.replaceWith(new TextNode(" "));
            }

        });
    }

    @NotNull
    private List<Pair<String, String>> getWikiPageLinks(Document doc) {
        List<Pair<String,String>> topLinks = new ArrayList<>();
        Optional.ofNullable(doc.select("p a")).ifPresent(links -> links.forEach(atag -> {
            String href = atag.attr("href");

            if (href != null && !href.isBlank()
                    && !href.contains(":")
                    && !href.startsWith("#")
            ) {
                topLinks.add(Pair.of(href, atag.attr("title")));
            }
        }));
        return topLinks;
    }


    @NotNull
    private List<String> getWikiPageLinkText(Document doc) {
        List<String> topLinks = new ArrayList<>();

        doc.select("p a,h1,h2,h3,h4,i,em,strong,b").forEach(e -> topLinks.add(e.text()));

        return topLinks;
    }

    @NotNull
    private List<Pair<String, String>> getDisambiguationLinks(Document doc) {
        List<Pair<String,String>> disambig = new ArrayList<>();


        Optional.ofNullable(doc.getElementsByClass("hatnote")).ifPresent(hatnotes -> {
            hatnotes.forEach(note -> {
                Optional.ofNullable(note.getElementsByTag("a"))
                    .ifPresent(links -> links.forEach(atag -> {
                        String href = atag.attr("href");
                        if (atag.hasClass("mw-disambig") && href != null) {
                            disambig.add(Pair.of(href, atag.attr("title")));
                        }
                    }));
            });
        });
        Optional.ofNullable(doc.getElementsByClass("hatnote")).ifPresent(Elements::remove);
        return disambig;
    }

    private void removeTag(Document doc, String... tags) {
        for (String tag : tags) {
            doc.getElementsByTag(tag).remove();
        }
    }
    private void removeByClass(Document doc, String... classes) {
        for (String clas: classes) {
            doc.getElementsByClass(clas).remove();
        }
    }
    private void removeIds(Document doc, String... ids) {
        Arrays.stream(ids)
                .map(doc::getElementById)
                .filter(Objects::nonNull)
                .forEach(Element::remove);
    }

    private void marginifyHeaders(Document doc) {
        Elements headers = doc.getElementsByTag("h4");
        if (headers.size() == 0) {
            headers = doc.getElementsByTag("h3");
        }
        headers.addClass("margin-note");
    }

    boolean isWikiClass(String clazz) {
        if ("verb".equals(clazz)) {
            return false;
        }
        if ("extern-link".equals(clazz)) {
            return false;
        }
        if ("margin-note".equals(clazz)) {
            return false;
        }
        return true;
    }

}
