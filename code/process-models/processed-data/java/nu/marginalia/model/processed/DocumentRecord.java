package nu.marginalia.model.processed;

import blue.strategic.parquet.Dehydrator;
import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.ValueWriter;
import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.list.array.TLongArrayList;
import lombok.*;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Types;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.roaringbitmap.RoaringBitmap;

import java.nio.ByteBuffer;
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
public class DocumentRecord {
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

    @Nullable
    public List<String> words;
    @Nullable
    public TLongList metas;
    @Nullable
    public List<RoaringBitmap> positions;

    public static Hydrator<DocumentRecord, DocumentRecord> newHydrator() {
        return new DocumentDataHydrator();
    }

    public static Dehydrator<DocumentRecord> newDehydrator() {
        return DocumentRecord::dehydrate;
    }

    public static MessageType schema = new MessageType(
            DocumentRecord.class.getSimpleName(),
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
            Types.optional(INT64).named("documentMetadata"),
            Types.optional(INT32).named("length"),
            Types.optional(FLOAT).named("quality"),
            Types.optional(INT32).named("pubYear"),
            Types.repeated(INT64).named("wordMeta"),
            Types.repeated(BINARY).named("positions"),
            Types.repeated(BINARY).as(stringType()).named("word")
    );

    @SneakyThrows
    public DocumentRecord add(String heading, Object value) {
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
            case "positions" -> {
                if (this.positions == null) {
                    this.positions = new ArrayList<>(100);
                }
                byte[] array = (byte[]) value;
                ByteBuffer buffer = ByteBuffer.wrap(array);
                var rb = new RoaringBitmap();
                rb.deserialize(buffer);
                this.positions.add(rb);
            }
            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }

    public void dehydrate(ValueWriter valueWriter) {
        valueWriter.write("domain", domain);
        valueWriter.write("url", url);
        valueWriter.write("ordinal", ordinal);
        valueWriter.write("state", state);

        if (stateReason != null)
            valueWriter.write("stateReason", stateReason);
        if (title != null)
            valueWriter.write("title", title);
        if (description != null)
            valueWriter.write("description", description);
        valueWriter.write("htmlFeatures", htmlFeatures);
        valueWriter.write("htmlStandard", htmlStandard);
        valueWriter.write("documentMetadata", documentMetadata);
        valueWriter.write("length", length);
        valueWriter.write("hash", hash);
        valueWriter.write("quality", quality);
        if (pubYear != null) {
            valueWriter.write("pubYear", pubYear);
        }
        if (metas != null) {
            valueWriter.writeList("wordMeta", metas);
        }
        if (positions != null) {
            List<byte[]> pos = new ArrayList<>(positions.size());
            for (RoaringBitmap bitmap : positions) {
                ByteBuffer baos = ByteBuffer.allocate(bitmap.serializedSizeInBytes());
                bitmap.serialize(baos);
                pos.add(baos.array());
            }
            valueWriter.writeList("positions", pos);
        }

        if (words != null) {
            valueWriter.writeList("word", words);
        }
    }

}

class DocumentDataHydrator implements Hydrator<DocumentRecord, DocumentRecord> {

    @Override
    public DocumentRecord start() {
        return new DocumentRecord();
    }

    @Override
    public DocumentRecord add(DocumentRecord target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public DocumentRecord finish(DocumentRecord target) {
        return target;
    }

}
