package nu.marginalia.converting.processor.pubdate.heuristic;

import nu.marginalia.converting.model.DocumentHeaders;
import nu.marginalia.converting.processor.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.pubdate.PubDateParser;
import nu.marginalia.model.DocumentFormat;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawl.PubDate;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;

import java.util.Optional;

public class PubDateHeuristicDOMParsingPass1 implements PubDateHeuristic {

    @Override
    public Optional<PubDate> apply(PubDateEffortLevel effortLevel, DocumentHeaders headers, EdgeUrl url, Document document, DocumentFormat htmlStandard) {
        if (effortLevel == PubDateEffortLevel.LOW)
            return Optional.empty();

        DateExtractingNodeVisitorPass filter = new DateExtractingNodeVisitorPass(htmlStandard);

        document.filter(filter);

        return Optional.ofNullable(filter.pubDate);
    }


    private static class DateExtractingNodeVisitorPass implements NodeFilter {
        public PubDate pubDate;
        private final DocumentFormat htmlStandard;

        private DateExtractingNodeVisitorPass(DocumentFormat htmlStandard) {
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

            if (text.length() < 32 && isCandidatForCopyrightNotice(text)) {
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
            if (text.contains("Posted on"))
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
                    || classes.contains("date")
                    || classes.contains("datey")
                    || el.id().contains("footer-info-lastmod"); // mediawiki
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
            if (htmlStandard == DocumentFormat.UNKNOWN) {
                PubDateParser
                        .dateFromHighestYearLookingSubstring(text)
                        .ifPresent(this::setPubDate);
            }
            else {
                PubDateParser
                        .attemptParseDate(text)
                        .ifPresent(this::setPubDate);
            }
        }


    }

}
