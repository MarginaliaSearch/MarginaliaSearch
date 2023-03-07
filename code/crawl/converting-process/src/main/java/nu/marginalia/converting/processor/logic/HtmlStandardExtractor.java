package nu.marginalia.converting.processor.logic;

import com.google.common.base.Strings;
import nu.marginalia.model.crawl.EdgeHtmlStandard;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlStandardExtractor {


    private static final Logger logger = LoggerFactory.getLogger(HtmlStandardExtractor.class);

    public static EdgeHtmlStandard parseDocType(DocumentType docType) {
        if (null == docType) {
            return EdgeHtmlStandard.UNKNOWN;
        }
        String publicId = docType.publicId();
        if (Strings.isNullOrEmpty(publicId))
            return EdgeHtmlStandard.HTML5;

        publicId = publicId.toUpperCase();
        if (publicId.startsWith("-//SOFTQUAD SOFTWARE//DTD") && publicId.contains("HTML 4")) {
            return EdgeHtmlStandard.HTML4;
        }
        if (publicId.startsWith("-//SOFTQUAD SOFTWARE//DTD") && publicId.contains("HTML 3")) {
            return EdgeHtmlStandard.HTML123;
        }
        if (publicId.startsWith("-//INTERNET/RFC XXXX//EN"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//NETSCAPE COMM. CORP"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//SQ//DTD HTML 2"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//SOFTQUAD//DTD HTML 2"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//W3O//DTD W3 HTML 2"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML 2"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML//EN"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-/W3C//DTD HTML 3"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-/W3C/DTD HTML 3"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML 3"))
            return EdgeHtmlStandard.HTML123;
        if (publicId.startsWith("-//W3C//DTD XHTML"))
            return EdgeHtmlStandard.XHTML;
        if (publicId.startsWith("ISO/IEC 15445:2000//DTD"))
            return EdgeHtmlStandard.XHTML;
        if (publicId.startsWith("-//W3C//DTD HTML"))
            return EdgeHtmlStandard.HTML4;

        logger.debug("Unknown publicID standard {}", publicId);
        return EdgeHtmlStandard.UNKNOWN;
    }

    public static EdgeHtmlStandard sniffHtmlStandard(Document parsed) {
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
            return EdgeHtmlStandard.HTML5;
        }
        if (html4Attributes > 0) {
            return EdgeHtmlStandard.HTML4;
        }
        return EdgeHtmlStandard.HTML123;
    }
}
