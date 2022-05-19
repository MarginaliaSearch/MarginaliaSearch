package nu.marginalia.wmsa.edge.tools;

import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.util.ParallelPipe;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlFeature;
import nu.marginalia.wmsa.edge.data.dao.EdgeDataStoreDaoImpl;
import nu.marginalia.wmsa.edge.index.client.EdgeIndexClient;
import nu.marginalia.wmsa.edge.integration.stackoverflow.StackOverflowPostProcessor;
import nu.marginalia.wmsa.edge.integration.BasicPageUploader;
import nu.marginalia.wmsa.edge.integration.stackoverflow.StackOverflowPostsReader;
import nu.marginalia.wmsa.edge.integration.model.BasicDocumentData;
import nu.marginalia.wmsa.edge.integration.stackoverflow.model.StackOverflowPost;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.crawl.EdgeDomainIndexingState;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.mariadb.jdbc.Driver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.EnumSet;

public class StackOverflowLoaderMain {
    public static void main(String[] args) throws InterruptedException {
        String site = args[0];
        String file = args[1];

        if (!Files.exists(Path.of(file))) {
            System.err.println("Invalid file " + file);
            return;
        }

        org.mariadb.jdbc.Driver driver = new Driver();

        EdgeDomain domain = new EdgeDomain(site);

        var ds = new DatabaseModule().provideConnection();

        EdgeDataStoreDaoImpl dataStoreDao = new EdgeDataStoreDaoImpl(ds);
        EdgeIndexClient indexClient = new EdgeIndexClient();

        dataStoreDao.putUrl(-2, new EdgeUrl("https", domain, null, "/"));
        setDomainToSpecial(ds, domain);

        var lm = new LanguageModels(
                Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
                Path.of("/var/lib/wmsa/model/tfreq-new-algo3.bin"),
                Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
                Path.of("/var/lib/wmsa/model/English.RDR"),
                Path.of("/var/lib/wmsa/model/English.DICT"),
                Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
        );


        var documentKeywordExtractor = new DocumentKeywordExtractor(new NGramDict(lm));
        BasicPageUploader uploader = new BasicPageUploader(dataStoreDao, indexClient,
                EnumSet.of(HtmlFeature.TRACKING, HtmlFeature.JS));
        ThreadLocal<StackOverflowPostProcessor> processor = ThreadLocal.withInitial(() -> new StackOverflowPostProcessor(new SentenceExtractor(lm), documentKeywordExtractor));

        var pipe = new ParallelPipe<StackOverflowPost, BasicDocumentData>("pipe", 32, 5, 2) {
            @Override
            public BasicDocumentData onProcess(StackOverflowPost stackOverflowPost) {
                return processor.get().process(stackOverflowPost);
            }

            @Override
            public void onReceive(BasicDocumentData stackOverflowIndexData) {
                uploader.upload(stackOverflowIndexData);
            }
        };

        System.out.println(domain);
        var reader = new StackOverflowPostsReader(file, domain, pipe::accept);
        reader.join();
        pipe.join();

        ds.close();
        indexClient.close();
    }

    private static void setDomainToSpecial(HikariDataSource ds, EdgeDomain domain) {
        try (var conn = ds.getConnection(); var stmt = conn.prepareStatement("UPDATE EC_DOMAIN SET STATE=? WHERE URL_PART=?")) {
            stmt.setInt(1, EdgeDomainIndexingState.SPECIAL.code);
            stmt.setString(2, domain.toString());
            stmt.executeUpdate();
        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }
    }
}
