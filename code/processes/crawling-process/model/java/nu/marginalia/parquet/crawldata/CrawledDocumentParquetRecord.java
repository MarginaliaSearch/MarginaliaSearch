package nu.marginalia.parquet.crawldata;

import blue.strategic.parquet.Dehydrator;
import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.ValueWriter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Types;

import java.time.Instant;

import static org.apache.parquet.schema.LogicalTypeAnnotation.stringType;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.*;

public class CrawledDocumentParquetRecord {
    public String domain;
    public String url;
    public String ip;
    public boolean cookies;
    public int httpStatus;
    public Instant timestamp;
    public String contentType;
    public byte[] body;

    public String headers;

    @Deprecated // will be replaced with the full headers field in the future
    public String etagHeader;
    @Deprecated // will be replaced with the full headers field in the future
    public String lastModifiedHeader;

    public CrawledDocumentParquetRecord(String domain, String url, String ip, boolean cookies, int httpStatus, Instant timestamp, String contentType, byte[] body, String headers, String etagHeader, String lastModifiedHeader) {
        this.domain = domain;
        this.url = url;
        this.ip = ip;
        this.cookies = cookies;
        this.httpStatus = httpStatus;
        this.timestamp = timestamp;
        this.contentType = contentType;
        this.body = body;
        this.headers = headers;
        this.etagHeader = etagHeader;
        this.lastModifiedHeader = lastModifiedHeader;
    }

    public CrawledDocumentParquetRecord() {
    }

    public static Hydrator<CrawledDocumentParquetRecord, CrawledDocumentParquetRecord> newHydrator() {
        return new CrawledDocumentParquetRecordHydrator();
    }

    public static Dehydrator<CrawledDocumentParquetRecord> newDehydrator() {
        return CrawledDocumentParquetRecord::dehydrate;
    }

    public static MessageType schema = new MessageType(
            CrawledDocumentParquetRecord.class.getSimpleName(),
            Types.required(BINARY).as(stringType()).named("domain"),
            Types.required(BINARY).as(stringType()).named("url"),
            Types.required(BINARY).as(stringType()).named("ip"),
            Types.required(BOOLEAN).named("cookies"),
            Types.required(INT32).named("httpStatus"),
            Types.required(INT64).named("epochSeconds"),
            Types.required(BINARY).as(stringType()).named("contentType"),
            Types.required(BINARY).named("body"),
            Types.optional(BINARY).as(stringType()).named("etagHeader"),
            Types.optional(BINARY).as(stringType()).named("lastModifiedHeader"),
            Types.optional(BINARY).as(stringType()).named("headers")
    );


    public CrawledDocumentParquetRecord add(String heading, Object value) {
        switch (heading) {
            case "domain" -> domain = (String) value;
            case "url" -> url = (String) value;
            case "ip" -> ip = (String) value;
            case "httpStatus" -> httpStatus = (Integer) value;
            case "cookies" -> cookies = (Boolean) value;
            case "contentType" -> contentType = (String) value;
            case "body" -> body = (byte[]) value;
            case "epochSeconds" -> timestamp = Instant.ofEpochSecond((Long) value);
            case "etagHeader" -> etagHeader = (String) value;
            case "lastModifiedHeader" -> lastModifiedHeader = (String) value;
            case "headers" -> headers = (String) value;

            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }

    public void dehydrate(ValueWriter valueWriter) {
        valueWriter.write("domain", domain);
        valueWriter.write("url", url);
        valueWriter.write("ip", ip);
        valueWriter.write("epochSeconds", timestamp.getEpochSecond());
        valueWriter.write("httpStatus", httpStatus);
        valueWriter.write("cookies", cookies);
        valueWriter.write("contentType", contentType);
        valueWriter.write("body", body);
        if (headers != null) {
            valueWriter.write("headers", headers);
        }
        if (etagHeader != null) {
            valueWriter.write("etagHeader", etagHeader);
        }
        if (lastModifiedHeader != null) {
            valueWriter.write("lastModifiedHeader", lastModifiedHeader);
        }
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof CrawledDocumentParquetRecord)) return false;
        final CrawledDocumentParquetRecord other = (CrawledDocumentParquetRecord) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$domain = this.domain;
        final Object other$domain = other.domain;
        if (this$domain == null ? other$domain != null : !this$domain.equals(other$domain)) return false;
        final Object this$url = this.url;
        final Object other$url = other.url;
        if (this$url == null ? other$url != null : !this$url.equals(other$url)) return false;
        final Object this$ip = this.ip;
        final Object other$ip = other.ip;
        if (this$ip == null ? other$ip != null : !this$ip.equals(other$ip)) return false;
        if (this.cookies != other.cookies) return false;
        if (this.httpStatus != other.httpStatus) return false;
        final Object this$timestamp = this.timestamp;
        final Object other$timestamp = other.timestamp;
        if (this$timestamp == null ? other$timestamp != null : !this$timestamp.equals(other$timestamp)) return false;
        final Object this$contentType = this.contentType;
        final Object other$contentType = other.contentType;
        if (this$contentType == null ? other$contentType != null : !this$contentType.equals(other$contentType))
            return false;
        if (!java.util.Arrays.equals(this.body, other.body)) return false;
        final Object this$headers = this.headers;
        final Object other$headers = other.headers;
        if (this$headers == null ? other$headers != null : !this$headers.equals(other$headers)) return false;
        final Object this$etagHeader = this.etagHeader;
        final Object other$etagHeader = other.etagHeader;
        if (this$etagHeader == null ? other$etagHeader != null : !this$etagHeader.equals(other$etagHeader))
            return false;
        final Object this$lastModifiedHeader = this.lastModifiedHeader;
        final Object other$lastModifiedHeader = other.lastModifiedHeader;
        if (this$lastModifiedHeader == null ? other$lastModifiedHeader != null : !this$lastModifiedHeader.equals(other$lastModifiedHeader))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof CrawledDocumentParquetRecord;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $domain = this.domain;
        result = result * PRIME + ($domain == null ? 43 : $domain.hashCode());
        final Object $url = this.url;
        result = result * PRIME + ($url == null ? 43 : $url.hashCode());
        final Object $ip = this.ip;
        result = result * PRIME + ($ip == null ? 43 : $ip.hashCode());
        result = result * PRIME + (this.cookies ? 79 : 97);
        result = result * PRIME + this.httpStatus;
        final Object $timestamp = this.timestamp;
        result = result * PRIME + ($timestamp == null ? 43 : $timestamp.hashCode());
        final Object $contentType = this.contentType;
        result = result * PRIME + ($contentType == null ? 43 : $contentType.hashCode());
        result = result * PRIME + java.util.Arrays.hashCode(this.body);
        final Object $headers = this.headers;
        result = result * PRIME + ($headers == null ? 43 : $headers.hashCode());
        final Object $etagHeader = this.etagHeader;
        result = result * PRIME + ($etagHeader == null ? 43 : $etagHeader.hashCode());
        final Object $lastModifiedHeader = this.lastModifiedHeader;
        result = result * PRIME + ($lastModifiedHeader == null ? 43 : $lastModifiedHeader.hashCode());
        return result;
    }

    public String toString() {
        return "CrawledDocumentParquetRecord(domain=" + this.domain + ", url=" + this.url + ", ip=" + this.ip + ", cookies=" + this.cookies + ", httpStatus=" + this.httpStatus + ", timestamp=" + this.timestamp + ", contentType=" + this.contentType + ", body=" + java.util.Arrays.toString(this.body) + ", headers=" + this.headers + ", etagHeader=" + this.etagHeader + ", lastModifiedHeader=" + this.lastModifiedHeader + ")";
    }
}

class CrawledDocumentParquetRecordHydrator implements Hydrator<CrawledDocumentParquetRecord, CrawledDocumentParquetRecord> {

    @Override
    public CrawledDocumentParquetRecord start() {
        return new CrawledDocumentParquetRecord();
    }

    @Override
    public CrawledDocumentParquetRecord add(CrawledDocumentParquetRecord target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public CrawledDocumentParquetRecord finish(CrawledDocumentParquetRecord target) {
        return target;
    }

}
