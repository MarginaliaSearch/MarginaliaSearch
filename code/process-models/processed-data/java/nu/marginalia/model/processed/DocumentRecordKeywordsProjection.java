package nu.marginalia.model.processed;

import blue.strategic.parquet.Hydrator;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import lombok.*;
import nu.marginalia.sequence.GammaCodedSequence;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class DocumentRecordKeywordsProjection {
    @NotNull
    public String domain;

    public int ordinal;

    public int htmlFeatures;
    public long documentMetadata;

    public List<String> words;
    public TLongList metas;
    public List<GammaCodedSequence> positions;

    public boolean hasKeywords() {
        return words != null && metas != null;
    }

    public static Hydrator<DocumentRecordKeywordsProjection, DocumentRecordKeywordsProjection> newHydrator() {
        return new DocumentRecordKeywordsProjectionHydrator();
    }

    public static Collection<String> requiredColumns() {
        return List.of("domain", "ordinal", "htmlFeatures", "word", "wordMeta", "documentMetadata");
    }

    @SneakyThrows
    public DocumentRecordKeywordsProjection add(String heading, Object value) {
        switch (heading) {
            case "domain" -> domain = (String) value;
            case "ordinal" -> ordinal = (Integer) value;
            case "htmlFeatures" -> htmlFeatures = (Integer) value;
            case "documentMetadata" -> documentMetadata = (Long) value;
            case "word" -> {
                if (this.words == null)
                    this.words = new ArrayList<>(100);
                this.words.add((String) value);
            }
            case "wordMeta" -> {
                if (this.metas == null) {
                    this.metas = new TLongArrayList(100);
                }
                this.metas.add((long) value);
            }
            case "position" -> {
                if (this.positions == null) {
                    this.positions = new ArrayList<>(100);
                }
                this.positions.add(new GammaCodedSequence((byte[]) value));
            }
            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }

}

class DocumentRecordKeywordsProjectionHydrator implements Hydrator<DocumentRecordKeywordsProjection, DocumentRecordKeywordsProjection> {

    @Override
    public DocumentRecordKeywordsProjection start() {
        return new DocumentRecordKeywordsProjection();
    }

    @Override
    public DocumentRecordKeywordsProjection add(DocumentRecordKeywordsProjection target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public DocumentRecordKeywordsProjection finish(DocumentRecordKeywordsProjection target) {
        return target;
    }

}
