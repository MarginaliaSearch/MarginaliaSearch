package nu.marginalia.wmsa.edge.crawler.domain.processor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.TextNode;

import java.util.regex.Pattern;

public class HtmlTagCleaner {
    public final int MAX_CODE_TAG_LENGTH = 32;
    public final Pattern codeTagJunkPattern = Pattern.compile("(\\.|&lt;|&gt;|<|>|\\([^)]*\\)[;]?$)");

    public void clean(Document doc) {
        cleanCodeTags(doc);

        doc.select("nav,form,input,code,body>title").remove();

        // Create "sentences" out of elements that sometimes lack a period at the end to help
        // NLP work better
        doc.select("li,h1,h2,h3,h4,h5,h6,td,th,p,div,title").forEach(e -> e.appendText(". "));
        doc.select("br,hr").forEach(e -> e.prependText(". "));
    }

    private void cleanCodeTags(Document doc) {
        for (var codeTag : doc.getElementsByTag("code")) {
            var text = codeTag.text();

            if (text.length() <= MAX_CODE_TAG_LENGTH) {
                codeTag.replaceWith(new TextNode(trimCodeTagContents(text)));
            }
            else {
                codeTag.remove();
            }

        }
    }

    private String trimCodeTagContents(String text) {
        return codeTagJunkPattern.matcher(text).replaceAll(" ");
    }
}
