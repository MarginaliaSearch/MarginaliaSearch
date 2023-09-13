package nu.marginalia.model.processed;

import blue.strategic.parquet.Dehydrator;
import blue.strategic.parquet.Hydrator;
import blue.strategic.parquet.ValueWriter;
import lombok.*;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Types;
import org.jetbrains.annotations.NotNull;

import static org.apache.parquet.schema.LogicalTypeAnnotation.stringType;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.BINARY;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DomainLinkRecord {
    @NotNull
    public String source;

    @NotNull
    public String dest;

    public void dehydrate(ValueWriter valueWriter) {
        valueWriter.write("source", source);
        valueWriter.write("dest", dest);
    }

    public static Dehydrator<DomainLinkRecord> newDehydrator() {
        return DomainLinkRecord::dehydrate;
    }

    public static Hydrator<DomainLinkRecord, DomainLinkRecord> newHydrator() {
        return new DomainLinkDataHydrator();
    }
    public static Hydrator<String, String> newDestDomainHydrator() {
        return new DestDomainNameHydrator();
    }

    public static MessageType schema = new MessageType(
            DomainLinkRecord.class.getSimpleName(),
            Types.required(BINARY).as(stringType()).named("source"),
            Types.required(BINARY).as(stringType()).named("dest")
    );

    public DomainLinkRecord add(String heading, Object value) {
        switch (heading) {
            case "source" -> source = (String) value;
            case "dest" -> dest = (String) value;
            default -> throw new UnsupportedOperationException("Unknown heading '" + heading + '"');
        }
        return this;
    }

}

class DomainLinkDataHydrator implements Hydrator<DomainLinkRecord, DomainLinkRecord> {

    @Override
    public DomainLinkRecord start() {
        return new DomainLinkRecord();
    }

    @Override
    public DomainLinkRecord add(DomainLinkRecord target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public DomainLinkRecord finish(DomainLinkRecord target) {
        return target;
    }

}

class DestDomainNameHydrator implements Hydrator<String, String> {

    @Override
    public String start() {
        return "";
    }

    @Override
    public String add(String target, String heading, Object value) {
        if ("dest".equals(heading)) {
            return (String) value;
        }
        return target;
    }

    @Override
    public String finish(String target) {
        return target;
    }
}