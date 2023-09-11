package nu.marginalia.model.processed;

import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
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
@ToString
public class ProcessedDocumentData {
    @NotNull
    public String domain;
    @NotNull
    public String url;

    public int ordinal;

    @NotNull
    public String state;
    @Nullable
    public String stateReason;

    @Nullable
    public String title;
    @Nullable
    public String description;
    public int htmlFeatures;
    @Nullable
    public String htmlStandard;

    public int length;
    public long hash;
    public float quality;

    @Nullable
    public Integer pubYear;

    @Nullable
    public List<String> words;
    @Nullable
    public List<Long> metas;

    public static MessageType schema = new MessageType(
            ProcessedDocumentData.class.getSimpleName(),
            Types.required(BINARY).as(stringType()).named("domain"),
            Types.required(BINARY).as(stringType()).named("url"),
            Types.required(INT32).named("ordinal"),
            Types.required(BINARY).as(stringType()).named("state"),
            Types.optional(BINARY).as(stringType()).named("stateReason"),
            Types.optional(BINARY).as(stringType()).named("title"),
            Types.optional(BINARY).as(stringType()).named("description"),
            Types.optional(INT32).named("htmlFeatures"),
            Types.optional(BINARY).as(stringType()).named("htmlStandard"),
            Types.optional(INT64).named("hash"),
            Types.optional(INT32).named("length"),
            Types.optional(FLOAT).named("quality"),
            Types.optional(INT32).named("pubYear"),
            Types.repeated(INT64).named("wordMeta"),
            Types.repeated(BINARY).as(stringType()).named("word")
    );

    public ProcessedDocumentData add(String heading, Object value) {
        switch (heading) {
            case "domain" -> domain = (String) value;
            case "url" -> url = (String) value;
            case "ordinal" -> ordinal = (Integer) value;
            case "htmlFeatures" -> htmlFeatures = (Integer) value;
            case "length" -> length = (Integer) value;
            case "pubYear" -> pubYear = (Integer) value;
            case "hash" -> hash = (Long) value;
            case "quality" -> quality = (Float) value;
            case "state" -> state = (String) value;
            case "stateReason" -> stateReason = (String) value;
            case "title" -> title = (String) value;
            case "description" -> description = (String) value;
            case "htmlStandard" -> htmlStandard = (String) value;
            case "word" -> {
                if (this.words == null)
                    this.words = new ArrayList<>(100);
                this.words.add((String) value);
            }
            case "wordMeta" -> {
                if (this.metas == null) {
                    this.metas = new ArrayList<>(100);
                }
                this.metas.add((Long) value);
            }
            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }
}
