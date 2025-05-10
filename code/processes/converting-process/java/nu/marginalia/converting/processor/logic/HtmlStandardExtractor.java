package nu.marginalia.converting.processor.logic;

import com.google.common.base.Strings;
import nu.marginalia.model.DocumentFormat;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.DocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HtmlStandardExtractor {


    private static final Logger logger = LoggerFactory.getLogger(HtmlStandardExtractor.class);

    public static DocumentFormat parseDocType(DocumentType docType) {
        if (null == docType) {
            return DocumentFormat.UNKNOWN;
        }

        String publicId = docType.publicId();
        if (Strings.isNullOrEmpty(publicId))
            return DocumentFormat.HTML5;

        publicId = publicId.toUpperCase();
        if (publicId.startsWith("-//SOFTQUAD SOFTWARE//DTD") && publicId.contains("HTML 4")) {
            return DocumentFormat.HTML4;
        }
        if (publicId.startsWith("-//SOFTQUAD SOFTWARE//DTD") && publicId.contains("HTML 3")) {
            return DocumentFormat.HTML123;
        }
        if (publicId.startsWith("-//INTERNET/RFC XXXX//EN"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//NETSCAPE COMM. CORP"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//SQ//DTD HTML 2"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//SOFTQUAD//DTD HTML 2"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//W3O//DTD W3 HTML 2"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML 2"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML//EN"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-/W3C//DTD HTML 3"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-/W3C/DTD HTML 3"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//IETF//DTD HTML 3"))
            return DocumentFormat.HTML123;
        if (publicId.startsWith("-//W3C//DTD XHTML"))
            return DocumentFormat.XHTML;
        if (publicId.startsWith("ISO/IEC 15445:2000//DTD"))
            return DocumentFormat.XHTML;
        if (publicId.startsWith("-//W3C//DTD HTML"))
            return DocumentFormat.HTML4;

        logger.debug("Unknown publicID standard {}", publicId);
        return DocumentFormat.UNKNOWN;
    }

    public static DocumentFormat sniffHtmlStandard(Document parsed) {
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
            return DocumentFormat.HTML5;
        }
        if (html4Attributes > 0) {
            return DocumentFormat.HTML4;
        }
        return DocumentFormat.HTML123;
    }
}
