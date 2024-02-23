package nu.marginalia.model.processed;

import blue.strategic.parquet.Hydrator;
import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DocumentRecordMetadataProjection {
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

    public long documentMetadata;

    @Nullable
    public Integer pubYear;

    public static Collection<String> requiredColumns() {
        return List.of("domain", "url", "ordinal", "htmlFeatures", "length", "pubYear",
                                 "hash", "documentMetadata", "quality", "state", "stateReason",
                                 "title", "description", "htmlStandard");
    }

    public DocumentRecordMetadataProjection add(String heading, Object value) {
        switch (heading) {
            case "domain" -> domain = (String) value;
            case "url" -> url = (String) value;
            case "ordinal" -> ordinal = (Integer) value;
            case "htmlFeatures" -> htmlFeatures = (Integer) value;
            case "length" -> length = (Integer) value;
            case "pubYear" -> pubYear = (Integer) value;
            case "hash" -> hash = (Long) value;
            case "documentMetadata" -> documentMetadata = (Long) value;
            case "quality" -> quality = (Float) value;
            case "state" -> state = (String) value;
            case "stateReason" -> stateReason = (String) value;
            case "title" -> title = (String) value;
            case "description" -> description = (String) value;
            case "htmlStandard" -> htmlStandard = (String) value;

            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }

    public static Hydrator<DocumentRecordMetadataProjection, DocumentRecordMetadataProjection> newHydrator() {
        return new DocumentRecordMetadataHydrator();
    }



}

class DocumentRecordMetadataHydrator implements Hydrator<DocumentRecordMetadataProjection, DocumentRecordMetadataProjection> {

    @Override
    public DocumentRecordMetadataProjection start() {
        return new DocumentRecordMetadataProjection();
    }

    @Override
    public DocumentRecordMetadataProjection add(DocumentRecordMetadataProjection target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public DocumentRecordMetadataProjection finish(DocumentRecordMetadataProjection target) {
        return target;
    }

}
