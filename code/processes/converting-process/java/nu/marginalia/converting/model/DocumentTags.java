package nu.marginalia.converting.model;

import nu.marginalia.dom.MeasureLengthVisitor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class DocumentTags {
    private final Elements scripts = new Elements();
    private final Elements anchors = new Elements();
    private final Elements metas = new Elements();
    private final Elements noscripts = new Elements();
    private final Elements frames = new Elements();
    private final Elements times = new Elements();
    private final Elements headings = new Elements();
    private final Elements bases = new Elements();

    private boolean hasDateTag = false;
    private boolean hasMediaTag = false;

    private final MeasureLengthVisitor lengthVisitor = new MeasureLengthVisitor();

    public DocumentTags(Document doc) {
        doc.traverse((node, depth) -> {
            lengthVisitor.head(node, depth);

            if (!(node instanceof Element el))
                return;

            switch (el.normalName()) {
                case "script" -> scripts.add(el);
                case "a" -> anchors.add(el);
                case "meta" -> metas.add(el);
                case "noscript" -> noscripts.add(el);
                case "frame", "iframe" -> frames.add(el);
                case "time" -> times.add(el);
                case "h1", "h2", "h3" -> headings.add(el);
                case "base" -> bases.add(el);
                case "date" -> hasDateTag = true;
                case "object", "audio", "video" -> hasMediaTag = true;
            }
        });
    }

    public Elements scriptTags() {
        return scripts;
    }

    /** All a tags in the document */
    public Elements aTags() {
        return anchors;
    }

    public Elements metaTags() {
        return metas;
    }

    public Elements noscriptTags() {
        return noscripts;
    }

    /** All frame and iframe tags in the document */
    public Elements frameTags() {
        return frames;
    }

    public Elements timeTags() {
        return times;
    }

    /** All h1, h2 and h3 tags in the document */
    public Elements allHeadingTags() {
        return headings;
    }

    public Elements baseTags() {
        return bases;
    }

    public boolean hasDateTag() {
        return hasDateTag;
    }

    /** Whether the document has an object, audio or video tag */
    public boolean hasMediaTag() {
        return hasMediaTag;
    }

    /** The text length of the document, as measured by MeasureLengthVisitor */
    public int textLength() {
        return lengthVisitor.length;
    }

    /** Matches an attribute value with the same semantics as jsoup's element[attr=value] selector */
    public static boolean attrIs(Element el, String key, String value) {
        String val = el.attr(key).trim();

        return value.equalsIgnoreCase(val);
    }
}
