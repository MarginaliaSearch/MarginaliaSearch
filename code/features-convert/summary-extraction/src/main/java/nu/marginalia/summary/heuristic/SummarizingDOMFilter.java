package nu.marginalia.summary.heuristic;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeFilter;

import java.util.*;
import java.util.function.Function;

import static nu.marginalia.summary.heuristic.HeuristicTextUtil.countOccurrencesOfAnyWord;
import static org.jsoup.internal.StringUtil.isActuallyWhitespace;
import static org.jsoup.internal.StringUtil.isInvisibleChar;

/** Traverses the DOM, identifies and branches that have a large ratio of text to tags
 * as this is most likely the text of an article.
 */

public class SummarizingDOMFilter implements NodeFilter {

    public Map<Node, NodeStatistics> statistics = new HashMap<>(10000);
    public Map<Node, Integer> pos = new HashMap<>(10000);
    public int cnt = 0;

    @Override
    public FilterResult head(Node node, int depth) {
        pos.put(node, cnt++);
        return FilterResult.CONTINUE;
    }

    @Override
    public FilterResult tail(Node node, int depth) {
        if (node instanceof TextNode tn) {
            statistics.put(node, new NodeStatistics(tn, 0, textLength(tn.getWholeText()), pos.getOrDefault(tn, cnt)));
        }
        else if (node instanceof Element e) {
            statistics.put(node, aggregateStatistics(e));

            if (shouldPruneTag(e)) {
                return FilterResult.REMOVE;
            }
        }

        return FilterResult.CONTINUE;
    }

    public boolean shouldPruneTag(Element tag) {
        String tagName = tag.tagName();

        if ("h1".equalsIgnoreCase(tagName)) return true;
        if ("h2".equalsIgnoreCase(tagName)) return true;
        if ("h3".equalsIgnoreCase(tagName)) return true;

        return false;
    }

    public String getSummary(int maxLength, Collection<String> importantWords) {
        List<NodeStatistics> ret = new ArrayList<>(statistics.size());
        for (var stats : statistics.values()) {
            if (stats.textToTagRatio() < 0.75) continue;
            if (!stats.isElement() || !stats.isAppropriateTagType()) continue;
            if (stats.textLength() < 64) continue;
            if (stats.isLink()) continue;

            ret.add(stats);
        }

        ret.sort(Comparator.comparing(e -> -e.textLength()));
        if (ret.size() > 32) ret.subList(32, ret.size()).clear();

        ret.sort(Comparator.comparing(NodeStatistics::pos));
        if (ret.size() > 16) ret.subList(16, ret.size()).clear();

        sortByWordRelevance(ret, importantWords);
        if (ret.size() > 8) ret.subList(8, ret.size()).clear();

        ret.sort(Comparator.comparing(NodeStatistics::isBody));
        if (ret.size() >= 1) {
            return StringUtils.abbreviate(ret.get(0).text(), "", maxLength);
        }
        return "";
    }


    // Words we don't want to appear in the summary
    private static List<String> badWords = List.of("copyright", "rights", "reserved", "post",
            "posted", "author", "published", "publish", "cookie", "cookies", "©", "terms", "conditions");

    private List<NodeStatistics> sortByWordRelevance(List<NodeStatistics> in,
                                                  Collection<String> importantWords) {

        if (importantWords.isEmpty())
            return in;

        Map<NodeStatistics, Integer> ret = new HashMap<>(in.size());

        // This is a relatively small list at this point
        // so this function isn't as bad as it looks

        for (var stats : in) {
            // text() is expensive, we don't mind sifting through superfluous whitespace
            int cnt = stats.score(tn ->
                    countOccurrencesOfAnyWord(tn.getWholeText(), importantWords)
                        - countOccurrencesOfAnyWord(tn.getWholeText(), badWords));

            if (cnt > 0) {
                ret.put(stats, -cnt);
            }
        }

        // Skip the sorting if we didn't match any importantWords
        if (ret.isEmpty()) {
            return in;
        }

        in.sort(Comparator.comparing(w -> ret.getOrDefault(w, 0)));

        return in;
    }

    private NodeStatistics aggregateStatistics(Element e) {
        int text = 0;
        int tag = 0;

        String tagName = e.tagName();
        if (!tagName.equalsIgnoreCase("br") && !tagName.equalsIgnoreCase("p")) {
            tag += tagName.length();
        }

        int numAttributes = e.attributesSize();
        tag += Math.max(numAttributes - 1, 0);

        if (numAttributes > 0) {
            var attrs = e.attributes();
            for (var attr : attrs) {
                if (Strings.isNullOrEmpty(attr.getValue()))
                    tag += attr.getKey().length();
                else {
                    tag += 3 + attr.getKey().length() + attr.getValue().length();
                }
            }
        }

        for (var childNode : e.childNodes()) {
            var cn = statistics.get(childNode);

            if (cn != null) {
                boolean isLink = (tagName.equalsIgnoreCase("a") || cn.isLink());
                if (isLink) {
                    tag += cn.tagLength + cn.textLength;
                }
                else {
                    text += cn.textLength;
                    tag += cn.tagLength;
                }

                if (!cn.isElement()) {
                    statistics.remove(cn.node);
                }
            }
        }

        return new NodeStatistics(e, tag, text, pos.getOrDefault(e, cnt));
    }

    private int textLength(String str) {
        int length = 0;

        // This is a modified version of JSoup's StringUtil.normaliseWhitespace()
        // that doesn't do allocation

        int len = str.length();
        int c;
        boolean lastWasWhite = false;
        boolean reachedNonWhite = false;

        for (int i = 0; i < len; i+= Character.charCount(c)) {
            c = str.codePointAt(i);
            if (isActuallyWhitespace(c)) {
                if ((!reachedNonWhite) || lastWasWhite)
                    continue;

                if (isAscii(c) && Character.isAlphabetic(c)) {
                    length++;
                }

                lastWasWhite = true;
            }
            else if (!isInvisibleChar(c)) {
                if (isAscii(c) && Character.isAlphabetic(c)) {
                    length++;
                }
                lastWasWhite = false;
                reachedNonWhite = true;
            }
        }

        return length;
    }

    public boolean isAscii(int cp) {
        return (cp & ~0x7F) == 0;
    }

    public record NodeStatistics(Node node, int tagLength, int textLength, int pos) {
        public double textToTagRatio() {
            if (textLength == 0) return 1;

            return textLength / (double)(tagLength + textLength);
        }

        public String text() {
            if (node instanceof Element e) {
                return e.text();
            }
            else if (node instanceof TextNode tn) {
                return tn.text();
            }
            return "";
        }
        public String wholeText() {
            if (node instanceof Element e) {
                return e.wholeText();
            }
            else if (node instanceof TextNode tn) {
                return tn.getWholeText();
            }
            return "";
        }

        public int score(Function<TextNode, Integer> fn) {
            int[] score = new int[1];

            node.traverse((node, depth) -> {
                if (node instanceof TextNode tn) {
                    score[0] += fn.apply(tn);
                }
            });

            return score[0];
        }

        public boolean isElement() {
            return node instanceof Element;
        }

        public boolean isLink() {
            if (node instanceof Element el) {
                return "a".equalsIgnoreCase(el.tagName());
            }
            return false;
        }

        public boolean isAppropriateTagType() {

            if (node instanceof Element el) {
                String tagName = el.tagName();
                if ("blockquote".equalsIgnoreCase(tagName))
                    return false;
                if ("tt".equalsIgnoreCase(tagName))
                    return false;
                if ("ol".equalsIgnoreCase(tagName))
                    return false;
                if ("ul".equalsIgnoreCase(tagName))
                    return false;
                if ("li".equalsIgnoreCase(tagName))
                    return false;
                if ("h1".equalsIgnoreCase(tagName))
                    return false;
                if ("h2".equalsIgnoreCase(tagName))
                    return false;
                if ("h3".equalsIgnoreCase(tagName))
                    return false;
                if ("th".equalsIgnoreCase(tagName))
                    return false;
                if ("td".equalsIgnoreCase(tagName))
                    return false;
                if ("tbody".equalsIgnoreCase(tagName))
                    return false;
                if ("html".equalsIgnoreCase(tagName))
                    return false;
                if ("title".equalsIgnoreCase(tagName))
                    return false;
                if ("#root".equalsIgnoreCase(tagName))
                    return false;
            }

            if (node.parent() instanceof Element elp) {
                if ("a".equals(elp.tagName()))
                    return false;
            }

            return true;
        }

        public boolean isBody() {
            if (node instanceof Element el) {
                return "body".equalsIgnoreCase(el.tagName());
            }
            return false;
        }

        public String tagName() {
            if (node instanceof Element el) {
                return el.tagName();
            }
            return '$'+node.getClass().getSimpleName();
        }

        public String toString() {
            return String.format("NodeStatistics[%s %d p %d %d]", tagName(), pos, tagLength, textLength);
        }

    }
}
