package nu.marginalia.model.processed;

import blue.strategic.parquet.Dehydrator;
import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.ValueWriter;
import lombok.*;
import org.apache.parquet.schema.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static org.apache.parquet.schema.LogicalTypeAnnotation.*;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DomainRecord {
    @NotNull
    public String domain;

    public int knownUrls;
    public int goodUrls;
    public int visitedUrls;

    @Nullable
    public String state;
    @Nullable
    public String redirectDomain;
    @Nullable
    public String ip;

    public List<String> rssFeeds;


    public static Hydrator<DomainRecord, DomainRecord> newHydrator() {
        return new DomainHydrator();
    }

    public static Dehydrator<DomainRecord> newDehydrator() {
        return DomainRecord::dehydrate;
    }

    public static Hydrator<DomainWithIp, DomainWithIp> newDomainNameHydrator() {
        return new DomainWithIpHydrator();
    }


    public static MessageType schema = new MessageType(
            DomainRecord.class.getSimpleName(),
            Types.required(BINARY).as(stringType()).named("domain"),
            Types.optional(INT32).named("knownUrls"),
            Types.optional(INT32).named("visitedUrls"),
            Types.optional(INT32).named("goodUrls"),
            Types.required(BINARY).as(stringType()).named("state"),
            Types.optional(BINARY).as(stringType()).named("redirectDomain"),
            Types.optional(BINARY).as(stringType()).named("ip"),
            Types.repeated(BINARY).as(stringType()).named("rss")
            );

    DomainRecord add(String heading, Object value) {
        switch (heading) {
            case "domain" -> domain = (String) value;
            case "knownUrls" -> knownUrls = (Integer) value;
            case "visitedUrls" -> visitedUrls = (Integer) value;
            case "goodUrls" -> goodUrls = (Integer) value;
            case "state" -> state = (String) value;
            case "redirectDomain" -> redirectDomain = (String) value;
            case "ip" -> ip = (String) value;
            case "rss" -> {
                if (rssFeeds == null) {
                    rssFeeds = new ArrayList<>();
                }
                rssFeeds.add((String) value);
            }
            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }

    private void dehydrate(ValueWriter valueWriter) {
        valueWriter.write("domain", domain);
        valueWriter.write("knownUrls", knownUrls);
        valueWriter.write("goodUrls", goodUrls);
        valueWriter.write("visitedUrls", visitedUrls);
        if (state != null) {
            valueWriter.write("state", state);
        }
        if (redirectDomain != null) {
            valueWriter.write("redirectDomain", redirectDomain);
        }
        if (ip != null) {
            valueWriter.write("ip", ip);
        }
        if (rssFeeds != null) {
            valueWriter.writeList("rss", rssFeeds);
        }
    }

}


class DomainHydrator implements Hydrator<DomainRecord, DomainRecord> {
    @Override
    public DomainRecord start() {
        return new DomainRecord();
    }

    @Override
    public DomainRecord add(DomainRecord target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public DomainRecord finish(DomainRecord target) {
        return target;
    }
}

class DomainWithIpHydrator implements Hydrator<DomainWithIp, DomainWithIp> {

    @Override
    public DomainWithIp start() {
        return new DomainWithIp();
    }

    @Override
    public DomainWithIp add(DomainWithIp target, String heading, Object value) {
        if ("domain".equals(heading)) {
            target.domain = (String) value;
        }
        else if ("ip".equals(heading)) {
            target.ip = (String) value;
        }
        return target;
    }

    @Override
    public DomainWithIp finish(DomainWithIp target) {
        return target;
    }
}