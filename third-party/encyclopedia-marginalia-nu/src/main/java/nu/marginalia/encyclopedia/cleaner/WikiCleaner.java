package nu.marginalia.encyclopedia.cleaner;

import nu.marginalia.encyclopedia.cleaner.model.ArticleData;
import nu.marginalia.encyclopedia.cleaner.model.ArticleParts;
import nu.marginalia.encyclopedia.model.Article;
import nu.marginalia.encyclopedia.model.Link;
import nu.marginalia.encyclopedia.model.LinkList;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class WikiCleaner {

    private static final String licenseFooter = "This article is issued from Wikipedia. The text is licensed under Creative Commons - Attribution - Sharealike. Additional terms may apply for the media files.";
    public ArticleData cleanWikiJunk(String url, String html) {
        return cleanWikiJunk(url, Jsoup.parse(html));
    }

    private boolean isPresentationRole(Element el) {
        return "presentation".equals(el.attr("role"));
    }
    private boolean isLicenseFooter(Element el) {
        // We'll add our own later
        if ("div".equals(el.tagName())) {
            return licenseFooter.equals(el.wholeOwnText().trim());
        }

        return false;
    }

    public ArticleData cleanWikiJunk(String url, Document doc) {

        if (doc.getElementById("content") == null) {
            return null;
        }

        List<Link> disambig = getDisambiguationLinks(doc);
        List<Link> topLinks = getWikiPageLinks(doc);

        doc.filter(CleanerFilter.builder()
                        .badClasses(Set.of("infobox", "collapsible", "navbar", "printfooter",
                                        "mw-editsection", "thumb", "sidebar", "navbox", "mw-jump-link",
                                        "vertical-navbox", "mw-indicators", "noprint", "sistersitebox",
                                        "BarChartTemplate"))
                        .badIds(Set.of("coordinates", "mw-page-base", "mw-head-base", "site-notice", "contentSub", "contentSub2"))
                        .badTags(Set.of("footer", "script", "object", "embed", "audio", "style", "nosript", "link", "meta", "img"))
                        .predicates(Set.of(this::isPresentationRole, this::isLicenseFooter))
                .build());

        doc.getElementsByTag("a").forEach(tag -> {
            var href = tag.attr("href");
            var parent = tag.parent();

            if (null != parent && "li".equals(parent.tagName())) {
                tag.removeAttr("title");

                if (href.startsWith("http://")) {
                    tag.addClass("extern-link");
                    tag.attr("rel", "nofollow");
                }
            } else {
                tag.replaceWith(new TextNode(tag.text()));
            }
        });

        doc.getElementsByTag("cite").tagName("span");

        doc.filter(CleanerFilter.builder()
                .badIds(Set.of("toc", "catlinks", "Notes", "mw-navigation", "mw-data-after-content", "jump-to-nav"))
                .badClasses(Set.of("mw-references-wrap", "references", "reference", "siteSub", "refbegin"))
                .build()
        );

        doc.getAllElements().forEach(elem -> {
            if (elem.parent() != null
                    && "summary".equals(elem.parent().tagName()))
            {
                elem.parent().replaceWith(elem);
            }
        });

        doc.getElementsByClass("mwe-math-element").forEach(mathSpan -> {
            var mathTag = mathSpan.getElementsByTag("math").first();
            if (mathTag != null) {
                mathSpan.replaceWith(mathTag);
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

        removeSingularlyNestedDivs(doc);

        removeEmptyTags(doc, "li");
        removeEmptyTags(doc, "ul");
        removeEmptyTags(doc, "div");

        doc.getElementsByTag("p").forEach(elem -> {
            if ("blockquote".equals(elem.parent().tagName())) {
                elem.replaceWith(new TextNode(elem.text()));
            }
        });

        removeEmptyTags(doc, "p");



        cascadingHeaderCleanup(doc, "h4", "h3", "h2");
        cascadingHeaderCleanup(doc, "h3", "h2");
        cascadingHeaderCleanup(doc, "h2");

        doc.getElementsByTag("table").forEach(table -> {
            table.attr("border", "1");

            if ("right".equals(table.attr("align"))) {
                table.remove();
            }
        });

        doc.getAllElements().forEach(elem -> {
            removeWikiClassNames(elem);

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

        doc.getElementsByTag("table").remove();

        // Remove the first header since we'll insert our own in the templating
        Optional.ofNullable(doc.getElementsByTag("h1").first()).ifPresent(Element::remove);

        ArticleParts articleParts = getDocumentParts(doc);

        return new Article(
                url,
                doc.title(),
                articleParts.getSummary(),
                articleParts,
                new LinkList(topLinks),
                new LinkList(disambig)
        ).asData();
    }

    private void removeWikiClassNames(Element elem) {
        final String classNames = elem.className();

        // Note that the string with class names isn't split,
        // this is fairly expensive and since most tags don't even
        // have classes, we'll optimistically check for presence and then
        // pay for the expensive removeClass operation even if unnecessary
        // due to a false positive

        if (classNames.contains("verb")) {
            elem.removeClass("verb");
        }

        if (classNames.contains("extern-link")) {
            elem.removeClass("extern-link");
        }

        if (classNames.contains("margin-note")) {
            elem.removeClass("margin-note");
        }

        if (classNames.contains("wikitable")) {
            elem.removeClass("wikitable");
        }

    }

    public static ArticleParts getDocumentParts(Document doc) {

        // We expect the document to be one container div with a bunch of children
        // each corresponding to a section of the document

        var rootDiv = doc.getElementsByTag("div").first();

        if (null == rootDiv) {
            return new ArticleParts(List.of());
        }

        // To be maximally useful, we want the article as a series of divs corresponding to
        // logical sections of the article

        List<String> parts = new ArrayList<>();

        Element normalizingDiv = null;
        for (Element child : rootDiv.children()) {
            boolean isDiv = "div".equals(child.tagName());

            if (!isDiv && normalizingDiv == null) {
                normalizingDiv = new Element("div");
            }

            if (isDiv && normalizingDiv != null) {
                if (normalizingDiv.childrenSize() > 0) {
                    parts.add(normalizingDiv.outerHtml());
                }
                normalizingDiv = null;
            }

            if (normalizingDiv != null) normalizingDiv.appendChild(child.clone());
            if (isDiv && child.childrenSize() > 0) parts.add(child.outerHtml());

        }
        if (normalizingDiv != null &&
            normalizingDiv.childrenSize() > 0)
        {
            parts.add(normalizingDiv.outerHtml());
        }

        return new ArticleParts(parts);
    }

    private void removeSingularlyNestedDivs(Document doc) {
        // Remove divs that only contain a single div, and replace them with the inner div

        for (Element div : doc.getElementsByTag("div")) {
            final Elements children = div.children();

            if (children.size() != 1)
                continue;

            final Element childDiv = children.first();

            if (null != childDiv && "div".equals(childDiv.tagName())) {
                div.replaceWith(childDiv);
            }
        }
    }

    private void cascadingHeaderCleanup(Document doc, String currH, String... nextHeaders) {
        doc.getElementsByTag(currH).forEach(elem -> {
            var next = elem.nextElementSibling();
            if (next == null) {
                elem.remove();
                return;
            }
            String nextTagName = next.tagName();
            if (currH.equals(nextTagName)) {
                elem.remove();
            }
            else for (String h : nextHeaders) {
                if (h.equals(nextTagName)) {
                    elem.remove();
                }
            }
        });
    }

    private void removeEmptyTags(Document doc, String tag) {
        doc.getElementsByTag(tag).forEach(elem -> {
            if (elem.text().isBlank() && elem.getElementsByTag("img").isEmpty()) {
                elem.replaceWith(new TextNode(" "));
            }
        });
    }

    @NotNull
    private List<Link> getWikiPageLinks(Document doc) {
        List<Link> topLinks = new ArrayList<>();
        doc.select("p a").forEach(atag -> {
            String href = atag.attr("href");

            if (!href.isBlank()
                    && !href.contains(":")
                    && !href.startsWith("#")
            ) {
                topLinks.add(new Link(href, atag.attr("title")));
            }
        });
        return topLinks;
    }


    @NotNull
    private List<Link> getDisambiguationLinks(Document doc) {
        List<Link> disambig = new ArrayList<>();

        for (var note: doc.getElementsByClass("hatnote")) {
            for (var atag : note.getElementsByTag("a")) {
                String href = atag.attr("href");
                if (atag.hasClass("mw-disambig") && !href.isBlank()) {
                    disambig.add(new Link(href, atag.attr("title")));
                }
            }
            note.remove();
        }

        return disambig;
    }

}
