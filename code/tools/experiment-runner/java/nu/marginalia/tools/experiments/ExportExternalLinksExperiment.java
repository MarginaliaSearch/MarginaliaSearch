package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import gnu.trove.set.hash.TLongHashSet;
import lombok.SneakyThrows;
import nu.marginalia.hash.MurmurHash3_128;
import nu.marginalia.io.crawldata.SerializableCrawlDataStream;
import nu.marginalia.link_parser.LinkParser;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawledDocument;
import nu.marginalia.tools.Experiment;
import org.jsoup.Jsoup;

import java.util.Objects;

public class ExportExternalLinksExperiment extends Experiment {



    @Inject
    public ExportExternalLinksExperiment() {

    }
    private static final LinkParser linkParser = new LinkParser();
    MurmurHash3_128 hash = new MurmurHash3_128();
    @SneakyThrows
    @Override
    public boolean process(SerializableCrawlDataStream stream) {
        TLongHashSet hashes = new TLongHashSet();

        while (stream.hasNext()) {
            if (!(stream.next() instanceof CrawledDocument doc))
                continue;
            if (null == doc.documentBody)
                continue;

            var baseUrl = new EdgeUrl(doc.url);
            var parsed = Jsoup.parse(doc.documentBody);

            for (var atag : parsed.getElementsByTag("a")) {
                String linkText = atag.text();
                if (linkText.isBlank())
                    continue;

                var linkOpt = linkParser.parseLinkPermissive(baseUrl, atag);
                linkOpt
                        .filter(url -> !Objects.equals(url.domain, baseUrl.domain))
                        .filter(url -> hashes.add(hash.hashNearlyASCII(linkText) ^ hash.hashNearlyASCII(url.toString())))
                        .ifPresent(url ->
                                System.out.printf("\"%s\",\"%s\",\"%s\"\n",
                                        csvify(url),
                                        csvify(baseUrl.domain),
                                        csvify(linkText)));
            }
        }

        return true;
    }

    private static String csvify(Object field) {
        return field.toString().replace("\"", "\"\"");
    }

    @Override
    public void onFinish() {
    }
}
