package nu.marginalia.converting.processor.logic;

import com.google.common.base.Strings;
import nu.marginalia.converting.model.HtmlStandard;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlStandardExtractor {


    private static final Logger logger = LoggerFactory.getLogger(HtmlStandardExtractor.class);

    public static HtmlStandard parseDocType(DocumentType docType) {
        if (null == docType) {
            return HtmlStandard.UNKNOWN;
        }

        String publicId = docType.publicId();
        if (Strings.isNullOrEmpty(publicId))
            return HtmlStandard.HTML5;

        publicId = publicId.toUpperCase();
        if (publicId.startsWith("-//SOFTQUAD SOFTWARE//DTD") && publicId.contains("HTML 4")) {
            return HtmlStandard.HTML4;
        }
        if (publicId.startsWith("-//SOFTQUAD SOFTWARE//DTD") && publicId.contains("HTML 3")) {
            return HtmlStandard.HTML123;
        }
        if (publicId.startsWith("-//INTERNET/RFC XXXX//EN"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//NETSCAPE COMM. CORP"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//SQ//DTD HTML 2"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//SOFTQUAD//DTD HTML 2"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//W3O//DTD W3 HTML 2"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML 2"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML//EN"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-/W3C//DTD HTML 3"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-/W3C/DTD HTML 3"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML 3"))
            return HtmlStandard.HTML123;
        if (publicId.startsWith("-//W3C//DTD XHTML"))
            return HtmlStandard.XHTML;
        if (publicId.startsWith("ISO/IEC 15445:2000//DTD"))
            return HtmlStandard.XHTML;
        if (publicId.startsWith("-//W3C//DTD HTML"))
            return HtmlStandard.HTML4;

        logger.debug("Unknown publicID standard {}", publicId);
        return HtmlStandard.UNKNOWN;
    }

    public static HtmlStandard sniffHtmlStandard(Document parsed) {
        int html4Attributes = 0;
        int html5Attributes = 0;

        if (parsed.getElementsByTag("article").size() > 0) html5Attributes++;
        if (parsed.getElementsByTag("header").size() > 0) html5Attributes++;
        if (parsed.getElementsByTag("footer").size() > 0) html5Attributes++;
        if (parsed.getElementsByTag("video").size() > 0) html5Attributes++;
        if (parsed.getElementsByTag("audio").size() > 0) html5Attributes++;
        if (parsed.getElementsByTag("canvas").size() > 0) html5Attributes++;
        if (parsed.getElementsByTag("link").stream().anyMatch(elem -> "stylesheet".equals(elem.attr("rel")))) {
            html4Attributes++;
        }
        if (html5Attributes > 0) {
            return HtmlStandard.HTML5;
        }
        if (html4Attributes > 0) {
            return HtmlStandard.HTML4;
        }
        return HtmlStandard.HTML123;
    }
}
