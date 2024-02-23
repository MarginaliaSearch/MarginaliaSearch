package nu.marginalia.converting.processor.logic.dom;

import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

/** Best effort visitor to measure the length of the text in a DOM tree
 * without allocating a bunch of Strings.
 */
public class MeasureLengthVisitor implements NodeVisitor {
    public int length = 0;

    @Override
    public void head(Node node, int depth) {
        if (node instanceof TextNode tn) {
            length += lengthOfElement(tn);
        }
    }

    // Emulate the HTML spec's definition of "length of an element"
    // in a "close-enough" fashion.
    static int lengthOfElement(TextNode tn) {
        String wholeText = tn.getWholeText();

        int length = 0;

        int start = 0;
        int end = wholeText.length() - 1;

        while (start < wholeText.length() && Character.isWhitespace(wholeText.charAt(start)))
            start++;
        while (end >= 0 && Character.isWhitespace(wholeText.charAt(end)))
            end--;

        boolean lastWasWhitespace = false;
        for (int i = start; i < end; i++) {
            char c = wholeText.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastWasWhitespace) {
                    length++;
                }

                lastWasWhitespace = true;
            } else {
                length++;

                lastWasWhitespace = false;
            }
        }

        return length;
    }
}
