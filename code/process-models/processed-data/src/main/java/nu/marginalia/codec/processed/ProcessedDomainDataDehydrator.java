package nu.marginalia.codec.processed;

import blue.strategic.parquet.Dehydrator;
import blue.strategic.parquet.ValueWriter;
import nu.marginalia.model.processed.ProcessedDomainData;

public class ProcessedDomainDataDehydrator implements Dehydrator<ProcessedDomainData> {


    @Override
    public void dehydrate(ProcessedDomainData record, ValueWriter valueWriter) {
        valueWriter.write("domain", record.domain);
        valueWriter.write("knownUrls", record.knownUrls);
        valueWriter.write("goodUrls", record.goodUrls);
        valueWriter.write("visitedUrls", record.visitedUrls);
        if (record.state != null) {
            valueWriter.write("state", record.state);
        }
        if (record.redirectDomain != null) {
            valueWriter.write("redirectDomain", record.redirectDomain);
        }
        if (record.ip != null) {
            valueWriter.write("ip", record.ip);
        }
    }
}
