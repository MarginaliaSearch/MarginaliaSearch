package nu.marginalia.codec.processed;

import blue.strategic.parquet.Hydrator;
import nu.marginalia.model.processed.ProcessedDocumentData;
import nu.marginalia.model.processed.ProcessedDomainData;

public class ProcessedDocumentDataHydrator implements Hydrator<ProcessedDocumentData, ProcessedDocumentData> {

    @Override
    public ProcessedDocumentData start() {
        return new ProcessedDocumentData();
    }

    @Override
    public ProcessedDocumentData add(ProcessedDocumentData target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public ProcessedDocumentData finish(ProcessedDocumentData target) {
        return target;
    }

}
