package nu.marginalia.model.body;

import nu.marginalia.contenttype.ContentType;
import nu.marginalia.contenttype.ContentTypeParser;
import nu.marginalia.contenttype.DocumentBodyToString;
import nu.marginalia.model.crawldata.CrawlerDocumentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DocumentBodyExtractor {
    private static final ContentTypeLogic contentTypeLogic = new ContentTypeLogic();

    private static final Logger logger = LoggerFactory.getLogger(DocumentBodyExtractor.class);

    /** Extract the body from a fetch result as a byte array. */
    public static DocumentBodyResult<byte[]> asBytes(HttpFetchResult result) {
        if (result instanceof HttpFetchResult.ResultOk fetchOk) {
            return asBytes(fetchOk);
        }
        else if (result instanceof HttpFetchResult.Result304ReplacedWithReference retained) {
            return new DocumentBodyResult.Ok<>(retained.contentType(), retained.body().getBytes());
        }

        return new DocumentBodyResult.Error<>(CrawlerDocumentStatus.ERROR, "Fetch Result Not Ok");
    }

    /** Extract the body from a fetch result as a string.  This function performs
     * content-type checks to ensure that the content-type is such that this operation
     * makes sense.
     *
     * @see ContentTypeLogic#isAllowableContentType(String)
     * */
    public static DocumentBodyResult<String> asString(HttpFetchResult result) {
        return asBytes(result).flatMap(DocumentBodyExtractor::toStringResult);
    }

    private static DocumentBodyResult<String> toStringResult(ContentType contentType, byte[] bytes) {
        if (contentTypeLogic.isAllowableContentType(contentType)) {
            try {
                return new DocumentBodyResult.Ok<>(contentType, DocumentBodyToString.getStringData(contentType, bytes));
            }
            catch (Exception ex) {
                return new DocumentBodyResult.Error<>(CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
            }
        }
        else {
            return new DocumentBodyResult.Error<>(CrawlerDocumentStatus.BAD_CONTENT_TYPE, "");
        }
    }

    /** Extract the body from a fetch result as a byte array. */
    public static DocumentBodyResult<byte[]> asBytes(HttpFetchResult.ResultOk rsp) {
        try {
            var byteStream = rsp.getInputStream();
            var contentTypeHeader = rsp.header("Content-Type");

            byte[] data = byteStream.readAllBytes(); // size is limited by WarcRecorder
            var contentType = ContentTypeParser.parseContentType(contentTypeHeader, data);

            return new DocumentBodyResult.Ok<>(contentType, data);
        } catch (Exception ex) {
            logger.error("Failed to extract body", ex);
            return new DocumentBodyResult.Error<>(CrawlerDocumentStatus.ERROR, "");
        }
    }

}
