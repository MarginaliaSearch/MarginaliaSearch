package nu.marginalia.tools;

import nu.marginalia.crawling.io.SerializableCrawlDataStream;
import nu.marginalia.crawling.model.CrawledDocument;
import nu.marginalia.crawling.model.CrawledDomain;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class LegacyExperiment extends Experiment {
    public abstract boolean process(CrawledDomain domain);

    @Override
    public boolean process(SerializableCrawlDataStream dataStream) throws IOException {
        List<CrawledDocument> documentList = new ArrayList<>();
        CrawledDomain domain = null;

        while (dataStream.hasNext()) {
            var nextObject = dataStream.next();
            if (nextObject instanceof CrawledDocument data) {
                documentList.add(data);
            }
            else if (nextObject instanceof CrawledDomain data) {
                domain = data;
            }
        }

        if (domain != null) {
            domain.doc.addAll(documentList);
            return process(domain);
        }
        return false;
    }
}
