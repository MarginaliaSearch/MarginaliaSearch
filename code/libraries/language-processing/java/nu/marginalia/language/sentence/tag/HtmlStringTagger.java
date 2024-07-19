package nu.marginalia.language.sentence.tag;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

import java.util.*;

/** A class that tags strings in an HTML document with the HTML
 * tags that are active at that point in the document.
 */
public class HtmlStringTagger implements NodeVisitor {
    private List<HtmlTag> tagStack = new ArrayList<>(8);
    private Set<Element> stackTags = new HashSet<>(8);
    private StringBuilder currentString = new StringBuilder(256);
    private List<HtmlTaggedString> output = new ArrayList<>();

    public static List<HtmlTaggedString> tagDocumentStrings(Document document) {
        var tagger = new HtmlStringTagger();
        document.traverse(tagger);
        return tagger.getOutput();
    }

    List<HtmlTaggedString> getOutput() {
        List<HtmlTaggedString> compactedOutput = new ArrayList<>(output.size());

        for (var ts : output) {
            if (compactedOutput.isEmpty()) {
                compactedOutput.add(ts);
            }
            else {
                var last = compactedOutput.getLast();
                if (last.tags().equals(ts.tags())) {
                    last.append(ts.string());
                }
                else {
                    compactedOutput.add(ts);
                }
            }
        }

        return output;
    }

    @Override
    public void head(Node node, int i) {
        if (node instanceof Element el) {
            String tagName = el.tagName();
            switch (tagName) {
                case "script" -> pushTag(HtmlTag.SCRIPT, el);
                case "style" -> pushTag(HtmlTag.STYLE, el);
                case "code" -> pushTag(HtmlTag.CODE, el);
                case "title" -> pushTag(HtmlTag.TITLE, el);
                case "nav" -> pushTag(HtmlTag.NAV, el);
                case "header" -> pushTag(HtmlTag.HEADER, el);
                case "footer" -> pushTag(HtmlTag.FOOTER, el);
                case "h1", "h2", "h3", "h4", "h5", "h6" -> pushTag(HtmlTag.HEADING, el);
            }
        }
        else if (node instanceof TextNode tn) {
            if (shouldProcess()) {
                String tnText = tn.text();
                if (!tnText.isBlank()) {
                    currentString = currentString.append(' ').append(tnText.trim());
                }
            }
        }
    }

    @Override
    public void tail(Node node, int i) {
        if (!(node instanceof Element el))
            return;

        if (stackTags.remove(el)) {
            output.add(new HtmlTaggedString(currentString, EnumSet.copyOf(tagStack)));
            tagStack.removeLast();
            currentString = new StringBuilder();
        }
        else if ("#root".equals(el.tagName())) {
            closeOngoingTag();
        }
    }

    private void pushTag(HtmlTag tag, Element el) {
        closeOngoingTag();

        tagStack.add(tag);
        stackTags.add(el);
    }

    private void closeOngoingTag() {
        if (currentString.isEmpty()) {
            return;
        }

        EnumSet<HtmlTag> tags;
        if (tagStack.isEmpty()) {
            tags = EnumSet.noneOf(HtmlTag.class);
        }
        else {
            tags = EnumSet.copyOf(tagStack);
        }

        output.add(new HtmlTaggedString(currentString, tags));
        currentString = new StringBuilder();
    }

    public boolean shouldProcess() {
        for (var tag : tagStack) {
            if (tag.exclude) {
                return false;
            }
        }
        return true;
    }

}