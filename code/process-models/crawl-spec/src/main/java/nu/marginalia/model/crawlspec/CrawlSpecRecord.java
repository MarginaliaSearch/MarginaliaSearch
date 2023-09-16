package nu.marginalia.model.crawlspec;

import blue.strategic.parquet.Dehydrator;
import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.ValueWriter;
import lombok.*;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.apache.parquet.schema.LogicalTypeAnnotation.stringType;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
@ToString
public class CrawlSpecRecord {
    @NotNull
    public String domain;


    /** Limit for how many documents will be crawled */
    public int crawlDepth;

    /** List of known URLs */
    @Nullable
    public List<String> urls;

    public static Hydrator<CrawlSpecRecord, CrawlSpecRecord> newHydrator() {
        return new CrawlSpecRecordHydrator();
    }

    public static Dehydrator<CrawlSpecRecord> newDehydrator() {
        return CrawlSpecRecord::dehydrate;
    }

    public static MessageType schema = new MessageType(
            CrawlSpecRecord.class.getSimpleName(),
            Types.required(BINARY).as(stringType()).named("domain"),
            Types.required(INT32).named("crawlDepth"),
            Types.repeated(BINARY).as(stringType()).named("urls")
    );

    public void dehydrate(ValueWriter valueWriter) {
        valueWriter.write("domain", domain);
        valueWriter.write("crawlDepth", crawlDepth);
        valueWriter.writeList("urls", urls);
    }

    public CrawlSpecRecord add(String heading, Object value) {
        switch (heading) {
            case "domain" -> domain = (String) value;
            case "crawlDepth" -> crawlDepth = (Integer) value;
            case "urls" -> {
                if (urls == null)
                    urls = new ArrayList<>();
                urls.add((String) value);
            }
        }

        return this;
    }
}

class CrawlSpecRecordHydrator implements Hydrator<CrawlSpecRecord, CrawlSpecRecord> {

    @Override
    public CrawlSpecRecord start() {
        return new CrawlSpecRecord();
    }

    @Override
    public CrawlSpecRecord add(CrawlSpecRecord target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public CrawlSpecRecord finish(CrawlSpecRecord target) {
        return target;
    }

}
