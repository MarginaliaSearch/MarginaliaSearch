package nu.marginalia.crawling.body;

import nu.marginalia.contenttype.ContentTypeParser;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.crawling.model.CrawlerDocumentStatus;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.zip.GZIPInputStream;

public class DocumentBodyExtractor {
    private static ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

    private static final Logger logger = LoggerFactory.getLogger(DocumentBodyExtractor.class);

    public static DocumentBodyResult extractBody(HttpFetchResult result) {
        if (result instanceof HttpFetchResult.ResultOk fetchOk) {
            return extractBody(fetchOk);
        }
        else {
            return new DocumentBodyResult.Error(CrawlerDocumentStatus.ERROR, "");
        }
    }

    public static DocumentBodyResult extractBody(HttpFetchResult.ResultOk rsp) {
        try {
            var byteStream = rsp.getInputStream();

            if ("gzip".equals(rsp.header("Content-Encoding"))) {
                byteStream = new GZIPInputStream(byteStream);
            }
            byteStream = new BOMInputStream(byteStream);

            var contentTypeHeader = rsp.header("Content-Type");
            if (contentTypeHeader != null && !contentTypeLogic.isAllowableContentType(contentTypeHeader)) {
                return new DocumentBodyResult.Error(CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
            }

            byte[] data = byteStream.readAllBytes(); // size is limited by WarcRecorder

            var contentType = ContentTypeParser.parseContentType(contentTypeHeader, data);
            if (!contentTypeLogic.isAllowableContentType(contentType.contentType())) {
                return new DocumentBodyResult.Error(CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
            }

            if ("Shift_JIS".equalsIgnoreCase(contentType.charset())) {
                return new DocumentBodyResult.Error(CrawlerDocumentStatus.BAD_CHARSET, "");
            }

            return new DocumentBodyResult.Ok(contentType.contentType(), DocumentBodyToString.getStringData(contentType, data));
        }
        catch (IOException ex) {
            logger.error("Failed to extract body", ex);
            return new DocumentBodyResult.Error(CrawlerDocumentStatus.ERROR, "");
        }
    }

}
