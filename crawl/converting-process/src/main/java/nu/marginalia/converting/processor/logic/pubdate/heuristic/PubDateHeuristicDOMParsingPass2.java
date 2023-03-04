package nu.marginalia.converting.processor.logic.pubdate.heuristic;

import nu.marginalia.model.crawl.EdgeHtmlStandard;
import nu.marginalia.model.crawl.PubDate;
import nu.marginalia.converting.processor.logic.pubdate.PubDateEffortLevel;
import nu.marginalia.converting.processor.logic.pubdate.PubDateHeuristic;
import nu.marginalia.converting.processor.logic.pubdate.PubDateParser;
import nu.marginalia.model.EdgeUrl;
import org.jetbrains.annotations.NotNull;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;

import java.util.Optional;

public class PubDateHeuristicDOMParsingPass2 implements PubDateHeuristic {

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

            if (hasPubDate()) {
                return FilterResult.STOP;
            }
            return FilterResult.CONTINUE;
        }

        public void onTextNode(TextNode tn) {
            String text = tn.getWholeText();

            if (isPossibleCandidate(text)) {
                parse(text);
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

    // This is basically the regex (^|[ ./\-])(\d{4})([ ./\-]$), but
    // unchecked regexes are too slow

    public static boolean isPossibleCandidate(String text) {
        if (text.length() >= 4 && text.length() < 24) {
            int ct = 0;
            char prevC = ' ';
            boolean goodStart = true;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isDigit(c)) {
                    if (ct++ == 0) {
                        goodStart = isGoodBreak(prevC);
                    }
                }
                else {
                    if (ct == 4 && goodStart && isGoodBreak(c)) return true;
                    else {
                        ct = 0;
                    }
                }
                prevC = c;
            }

            if (ct == 4 && goodStart)
                return true;
        }
        return false;
    }

    private static boolean isGoodBreak(char c) {
        return "./-,".indexOf(c) >= 0 || Character.isSpaceChar(c);
    }

}
