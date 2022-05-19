package nu.marginalia.gemini.gmi.renderer;

import nu.marginalia.gemini.gmi.line.*;
import nu.marginalia.wmsa.memex.model.MemexNodeUrl;
import nu.marginalia.wmsa.memex.model.MemexUrl;
import org.apache.logging.log4j.util.Strings;

import java.util.Objects;
import java.util.stream.Collectors;

public class GemtextRendererFactory {

    public final String urlBase;
    public final String docUrl;

    public GemtextRendererFactory(String urlBase, String docUrl) {
        this.urlBase = Objects.requireNonNull(urlBase, "urlBase must not be null");
        this.docUrl = Objects.requireNonNull(docUrl, "docUrl must not be null");
    }

    public GemtextRendererFactory(String urlBase) {
        this.urlBase = Objects.requireNonNull(urlBase, "urlBase must not be null");
        this.docUrl = null;
    }

    public GemtextRendererFactory() {
        this.urlBase = null;
        this.docUrl = null;
    }

    public GemtextRenderer htmlRendererEditable() {
        return new GemtextRenderer(this::htmlHeadingEditable,
                this::htmlLink, this::htmlList,
                this::htmlPre, this::htmlQuote,
                this::htmlText, this::htmlAside,
                this::htmlTask, this::htmlLiteral,
                this::htmlPragma);
    }

    public GemtextRenderer htmlRendererReadOnly() {
        return new GemtextRenderer(this::htmlHeadingReadOnly,
                this::htmlLink, this::htmlList,
                this::htmlPre, this::htmlQuote,
                this::htmlText, this::htmlAside,
                this::htmlTask, this::htmlLiteral,
                this::htmlPragma);
    }


    public GemtextRenderer gemtextRendererAsIs() {
        return new GemtextRenderer(this::rawHeading,
                this::rawLink, this::rawList,
                this::rawPre, this::rawQuote,
                this::rawText, this::rawAside,
                this::rawTask, this::rawLiteral,
                this::rawPragma);
    }


    public GemtextRenderer gemtextRendererPublic() {
        return new GemtextRenderer(this::rawHeading,
                this::rawLink, this::rawList,
                this::rawPre, this::rawQuote,
                this::rawText, this::rawAside,
                this::rawTask, this::rawLiteral,
                this::rawSupressPragma);
    }


    private String htmlPragma(GemtextPragma gemtextPragma) {
        return "<!-- pragma: " + sanitizeText(gemtextPragma.getLine()) + " -->\n";
    }

    public String htmlHeadingEditable(GemtextHeading g) {
        if (docUrl == null) {
            throw new UnsupportedOperationException("Wrong constructor used, need urlBase and docUrl");
        }
//        String editLink = String.format("\n<a class=\"utility\" href=\"%s/edit/%s\">Edit</a>\n", urlBase + docUrl, g.getLevel());

        return htmlHeadingReadOnly(g);
    }

    public String htmlHeadingReadOnly(GemtextHeading g) {
        if (g.getLevel().getLevel() == 1)
            return String.format("<h1 id=\"%s\">%s</h1>\n", g.getLevel(), sanitizeText(g.getName()));
        if (g.getLevel().getLevel() == 2)
            return String.format("<h2 id=\"%s\">%s</h2>\n", g.getLevel(), sanitizeText(g.getName()));
        if (g.getLevel().getLevel() == 3)
            return String.format("<h3 id=\"%s\">%s</h3>\n", g.getLevel(), sanitizeText(g.getName()));

        return String.format("<h4 id=\"%s\">%s</h4>\n", g.getLevel(), sanitizeText(g.getName()));
    }

    public String htmlLink(GemtextLink g) {
        if (urlBase == null) {
            throw new UnsupportedOperationException("Wrong constructor used, need urlBase");
        }
        final String linkClass = getLinkClass(g.getUrl());
        final String linkUrl = getLinkUrl(g.getUrl()).replaceFirst("^gemini://", "https://proxy.vulpes.one/gemini/");
        if (g.getTitle() != null) {
            return String.format("<dl class=\"link\"><dt><a class=\"%s\" href=\"%s\">%s</a></dt><dd>%s</dd></dl>\n",
                    linkClass, linkUrl, g.getUrl(), sanitizeText(g.getTitle()));
        }
        else {
            return String.format("<a class=\"%s\" href=\"%s\">%s</a><br>\n",
                    linkClass, linkUrl, g.getUrl());
        }
    }
    private String getLinkUrl(MemexUrl url) {
        if (url instanceof MemexNodeUrl || url.getUrl().startsWith("/")) {
            return urlBase + url;
        }
        return url.toString();
    }

    private String getLinkClass(MemexUrl url) {
        if (url instanceof MemexNodeUrl) {
            return "internal";
        }
        return "external";
    }
    public String htmlList(GemtextList g) {
        return g.getItems()
                .stream()
                .map(s -> "<li>" + sanitizeText(s) + "</li>")
                .collect(
                        Collectors.joining("\n", "<ul>\n", "</ul>\n"));
    }

    public String htmlPre(GemtextPreformat g) {
        return g.getItems().stream()
                .map(this::sanitizeText)
                .collect(
                Collectors.joining("\n", "<pre>\n", "</pre>\n"));
    }

    public String htmlLiteral(GemtextTextLiteral g) {
        return g.getItems().stream()
                .map(this::sanitizeText)
                .collect(
                Collectors.joining("\n", "<pre class=\"literal\">\n", "</pre>\n"));
    }
    public String htmlQuote(GemtextQuote g) {
        return g.getItems().stream()
                .map(this::sanitizeText)
                .collect(
                Collectors.joining("<br>\n", "<blockquote>\n", "</blockquote>\n"));

    }
    public String htmlText(GemtextText g) {
        return sanitizeText(g.getLine()) + "<br>\n";
    }
    public String htmlAside(GemtextAside g) {
        return "<aside>" + sanitizeText(g.getLine()) + "</aside>\n";
    }

    public String sanitizeText(String s) {
        return s.replaceAll("<", "&lt;").replaceAll(">", "&gt;");
    }

    public String htmlTask(GemtextTask g) {
        return String.format("<a class=\"task-pointer\" name=\"t%s\"></a><div class=\"task %s\" id=\"%s\">%s %s</div>\n",
                g.getId(),
                g.getState().style,
                g.getId(),
                "-".repeat(g.getLevel()),
                g.getTask());
    }

    public String rawHeading(GemtextHeading g) {
        if (g.getLevel().getLevel() == 1)
            return "# " + g.getName();
        if (g.getLevel().getLevel() == 2)
            return "## " + g.getName();
        if (g.getLevel().getLevel() == 3)
            return "### " + g.getName();

        return "### " + g.getName();
    }

    public String rawLink(GemtextLink g) {
        if (g.getTitle() != null && !g.getTitle().isBlank()) {
            return "=> " + g.getUrl().getUrl() + "\t" + g.getTitle();
        }
        return "=> " + g.getUrl().getUrl();
    }

    public String rawList(GemtextList g) {
        return g.getItems()
                .stream()
                .map(s -> "* " + s)
                .collect(Collectors.joining("\n"));
    }

    public String rawPre(GemtextPreformat g) {
        return g.getItems().stream()
                .collect(Collectors.joining("\n", "```\n", "\n```"));
    }

    public String rawQuote(GemtextQuote g) {
        return g.getItems().stream()
                .map(s -> "> " + s)
                .collect(Collectors.joining());

    }

    public String rawText(GemtextText g) {
        return g.getLine();
    }

    public String rawLiteral(GemtextTextLiteral g) {
        return Strings.join(g.getItems(), '\n');
    }

    public String rawAside(GemtextAside g) {
        return "(" + g.getLine() + ")";
    }
    public String rawTask(GemtextTask g) {
        return "-".repeat(Math.max(0, g.getLevel())) + " " + g.getTask();
    }
    private String rawPragma(GemtextPragma gemtextPragma) {
        return "%%% " + gemtextPragma.getLine();
    }
    private String rawSupressPragma(GemtextPragma gemtextPragma) {
        return "";
    }
}
