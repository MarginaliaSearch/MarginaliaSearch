package nu.marginalia.codec.processed;

import blue.strategic.parquet.Dehydrator;
import blue.strategic.parquet.ValueWriter;
import nu.marginalia.model.processed.ProcessedDocumentData;

public class ProcessedDocumentDataDehydrator implements Dehydrator<ProcessedDocumentData> {
    @Override
    public void dehydrate(ProcessedDocumentData record, ValueWriter valueWriter) {
        valueWriter.write("domain", record.domain);
        valueWriter.write("url", record.url);
        valueWriter.write("ordinal", record.ordinal);
        valueWriter.write("state", record.state);

        if (record.stateReason != null)
            valueWriter.write("stateReason", record.stateReason);
        if (record.title != null)
            valueWriter.write("title", record.title);
        if (record.description != null)
            valueWriter.write("description", record.description);
        valueWriter.write("htmlFeatures", record.htmlFeatures);
        valueWriter.write("htmlStandard", record.htmlStandard);
        valueWriter.write("length", record.length);
        valueWriter.write("hash", record.hash);
        valueWriter.write("quality", record.quality);
        if (record.pubYear != null) {
            valueWriter.write("pubYear", record.pubYear);
        }

        if (record.metas != null) {
            valueWriter.writeList("wordMeta", record.metas);
        }
        if (record.words != null) {
            valueWriter.writeList("word", record.words);
        }
    }
}
