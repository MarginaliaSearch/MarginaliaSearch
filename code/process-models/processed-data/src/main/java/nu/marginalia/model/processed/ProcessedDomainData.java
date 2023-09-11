package nu.marginalia.model.processed;

import lombok.*;
import org.apache.parquet.schema.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.parquet.schema.LogicalTypeAnnotation.*;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT32;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProcessedDomainData {
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

    public static MessageType schema = new MessageType(
            ProcessedDomainData.class.getSimpleName(),
            Types.required(BINARY).as(stringType()).named("domain"),
            Types.optional(INT32).named("knownUrls"),
            Types.optional(INT32).named("visitedUrls"),
            Types.optional(INT32).named("goodUrls"),
            Types.required(BINARY).as(stringType()).named("state"),
            Types.optional(BINARY).as(stringType()).named("redirectDomain"),
            Types.optional(BINARY).as(stringType()).named("ip"));

    public ProcessedDomainData add(String heading, Object value) {
        switch (heading) {
            case "domain" -> domain = (String) value;
            case "knownUrls" -> knownUrls = (Integer) value;
            case "visitedUrls" -> visitedUrls = (Integer) value;
            case "goodUrls" -> goodUrls = (Integer) value;
            case "state" -> state = (String) value;
            case "redirectDomain" -> redirectDomain = (String) value;
            case "ip" -> ip = (String) value;
            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }
}
