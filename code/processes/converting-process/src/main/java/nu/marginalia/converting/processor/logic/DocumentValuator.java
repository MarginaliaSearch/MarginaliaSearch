package nu.marginalia.converting.processor.logic;

import crawlercommons.utils.Strings;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.converting.model.HtmlStandard;
import nu.marginalia.converting.model.DisqualifiedException;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

public class DocumentValuator {

    public double getQuality(CrawledDocument crawledDocument,
                             HtmlStandard htmlStandard,
                             Document parsedDocument,
                             int textLength) throws DisqualifiedException {
        double scriptPenalty = getScriptPenalty(parsedDocument);

        int textBodyLength = textLength;
        int rawLength = crawledDocument.documentBody.length();

        if (textBodyLength == 0) {
            throw new DisqualifiedException(DisqualifiedException.DisqualificationReason.LENGTH);
        }

        return Math.log(textBodyLength / (double) (1+rawLength))*htmlStandard.scale
                + htmlStandard.offset
                - scriptPenalty;
    }


    private int getScriptPenalty(Document parsed) {
        var scriptVisitor = new ScriptVisitor();

        parsed.getElementsByTag("script").traverse(scriptVisitor);

        return scriptVisitor.score();
    }

    private static class ScriptVisitor implements NodeVisitor {
        boolean hasBadScript = false;
        int scriptLength = 0;
        double penalty = 0.;

        public int score() {
            return (int)(penalty + (hasBadScript?1:0) + (scriptLength)/1000.);
        }

        @Override
        public void head(@NotNull Node node, int depth) {
            if (node instanceof Element el) {
                visitTag(el);
            }
            else if (node instanceof TextNode tn) {
                visitScriptText(tn);

            }
        }

        private void visitScriptText(TextNode tn) {
            String wholeText = tn.getWholeText();
            scriptLength += wholeText.length();

            if (!hasBadScript) {
                hasBadScript = wholeText.contains(".createElement(");
            }
        }

        public void visitTag(Element el) {
            String srcAttr = el.attr("src");
            if (srcAttr.contains("wp-content") || srcAttr.contains("wp-includes") || srcAttr.contains("jquery")) {
                penalty += 0.49;
            }
            else if (!Strings.isBlank(srcAttr)) {
                penalty += 1;
            }
        }
    }
}
