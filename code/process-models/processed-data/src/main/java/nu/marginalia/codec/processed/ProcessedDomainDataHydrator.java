package nu.marginalia.codec.processed;

import blue.strategic.parquet.Hydrator;
import nu.marginalia.model.processed.ProcessedDomainData;

public class ProcessedDomainDataHydrator implements Hydrator<ProcessedDomainData, ProcessedDomainData> {

    @Override
    public ProcessedDomainData start() {
        return new ProcessedDomainData();
    }

    @Override
    public ProcessedDomainData add(ProcessedDomainData target, String heading, Object value) {
        return target.add(heading, value);
    }

    @Override
    public ProcessedDomainData finish(ProcessedDomainData target) {
        return target;
    }

}
