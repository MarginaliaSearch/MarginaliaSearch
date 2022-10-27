package nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.heuristic;

import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDate;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateEffortLevel;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.wmsa.edge.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;

import java.util.Optional;

public class PubDateHeuristicDOMParsing implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, String headers, EdgeUrl url, Document document, EdgeHtmlStandard htmlStandard) {
        if (effortLevel == PubDateEffortLevel.LOW)
            return Optional.empty();

        DateExtractingNodeVisitor filter = new DateExtractingNodeVisitor(htmlStandard);

        document.filter(filter);

        return Optional.ofNullable(filter.pubDate);
    }


    private static class DateExtractingNodeVisitor implements NodeFilter {
        public PubDate pubDate;
        private final EdgeHtmlStandard htmlStandard;

        private DateExtractingNodeVisitor(EdgeHtmlStandard htmlStandard) {
            this.htmlStandard = htmlStandard;
        }

        @NotNull
        @Override
        public FilterResult head(@NotNull Node node, int depth) {
            if (node instanceof TextNode tn) onTextNode(tn);
            if (node instanceof Element el) onElementNode(el);

            if (hasPubDate()) {
                return FilterResult.STOP;
            }
            return FilterResult.CONTINUE;
        }

        public void onTextNode(TextNode tn) {
            String text = tn.getWholeText();

            if (isCandidatForCopyrightNotice(text)) {
                parse(text);
            }
        }

        public void onElementNode(Element el) {
            if (hasCommonClass(el)) {
                parse(el.text());
            }

            if (!hasPubDate())
                tryParsePhpBBDate(el);
        }


        public boolean isCandidatForCopyrightNotice(String text) {
            if (text.contains("ublished"))
                return true;
            if (text.contains("opyright"))
                return true;
            if (text.contains("&copy;"))
                return true;
            if (text.contains("(c)"))
                return true;

            return false;
        }


        public boolean hasCommonClass(Element el) {
            var classes = el.classNames();

            return classes.contains("entry-meta") // wordpress
                    || classes.contains("byline")
                    || classes.contains("author")
                    || classes.contains("submitted")
                    || classes.contains("footer-info-lastmod"); // mediawiki
        }

        public void tryParsePhpBBDate(Element el) {

            /* Match HTML on the form <div>[...] <b>Posted:</b> Sun Oct 03, 2010 5:37 pm&nbsp;</div>
             * this is used on old phpBB message boards
             *
             * Schematically the DOM looks like this
             *
             *              b - TextNode[ Sun Oct 03, 2010 5:37 pm&nbsp;]
             *              |
             *          TextNode[Posted:]
             */
            if ("b".equals(el.tagName())
                        && el.childNodeSize() == 1
                        && el.childNode(0) instanceof TextNode ctn
                        && "Posted:".equals(ctn.getWholeText())
                        && el.nextSibling() instanceof TextNode ntn
                )
            {
                parse(ntn.getWholeText());
            }
        }


        public boolean hasPubDate() {
            return pubDate != null;
        }
        public void setPubDate(PubDate pubDate) {
            this.pubDate = pubDate;
        }

        @NotNull
        @Override
        public FilterResult tail(@NotNull Node node, int depth) {
            return FilterResult.CONTINUE;
        }

        private void parse(String text) {
            if (htmlStandard == EdgeHtmlStandard.UNKNOWN) {
                PubDateParser
                        .dateFromHighestYearLookingSubstring(text)
                        .ifPresent(this::setPubDate);
            }
            else {
                PubDateParser
                        .dateFromHighestYearLookingSubstringWithGuess(text, htmlStandard.yearGuess)
                        .ifPresent(this::setPubDate);
            }
        }


    }


}
