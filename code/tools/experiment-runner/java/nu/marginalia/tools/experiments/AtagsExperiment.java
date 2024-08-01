package nu.marginalia.tools.experiments;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.ProcessConfiguration;
import nu.marginalia.atags.AnchorTextKeywords;
import nu.marginalia.atags.source.AnchorTagsSource;
import nu.marginalia.atags.source.AnchorTagsSourceFactory;
import nu.marginalia.model.EdgeDomain;
import nu.marginalia.model.EdgeUrl;
import nu.marginalia.model.crawldata.CrawledDomain;
import nu.marginalia.tools.LegacyExperiment;

import java.sql.SQLException;

public class AtagsExperiment extends LegacyExperiment {


    private final AnchorTextKeywords keywords;
    private final AnchorTagsSource source;

    @Inject
    public AtagsExperiment(AnchorTextKeywords keywords, HikariDataSource dataSource) throws SQLException {
        this.keywords = keywords;
        this.source = new AnchorTagsSourceFactory(dataSource, new ProcessConfiguration(null, 1, null))
                .create();

    }

    @Override
    @SneakyThrows
    public boolean process(CrawledDomain domain) {
        var atags = source.getAnchorTags(new EdgeDomain(domain.domain));
        for (var doc : domain.doc) {
            if (doc.documentBody == null)
                continue;

            var newKeywords = keywords.getAnchorTextKeywords(atags, new EdgeUrl(doc.url));
            if (!newKeywords.isEmpty()) {
                System.out.println(newKeywords + " " + doc.url);
            }
        }
        return true;
    }

    @Override
    @SneakyThrows
    public void onFinish() {
        source.close();
    }
}
