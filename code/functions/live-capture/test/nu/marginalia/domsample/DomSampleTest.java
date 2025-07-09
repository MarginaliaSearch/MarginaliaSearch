package nu.marginalia.domsample;

import nu.marginalia.domsample.db.DomSampleDb;
import nu.marginalia.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;

public class DomSampleTest {

    static Path dbPath = Path.of("/home/vlofgren/Work/ds/dom-sample.db");

    @BeforeAll
    public static void setUpAll() throws SQLException {
        String dbUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    @AfterAll
    public static void tearDownAll() throws SQLException {
    }

    record Request(String method, long ts, URI url) {}
    record Sample(EdgeUrl url, List<Request> requests, String document, boolean accepted_popover) {}

    Map<String, Integer> counter = new HashMap<>();
    Map<String, Integer> counterTop = new HashMap<>();


    @Test
    public void test() throws Exception {

        DomSampleClassifier classifier;
        try (var resourceStream = getClass().getClassLoader().getResourceAsStream("request-classifier.xml")) {
            if (resourceStream == null) throw new IllegalArgumentException("Failed to load resource");
            classifier = new DomSampleClassifier(resourceStream);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


        try (DomSampleDb db = new DomSampleDb(dbPath)) {
            db.forEachSample(sample -> {
                var classifications = classifier.classify(sample);
                if (!classifications.isEmpty()) {
                    var popover = !Jsoup.parse(sample.sample()).select("*[data-position=fixed]").isEmpty();
                    System.out.println("Classifications for " + sample.url() + " " + (sample.acceptedPopover() || popover) + ": " + classifications);
                }

                return true;
            });
        }



        counter.entrySet().stream().filter(e -> e.getValue() > 5_000)
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue()));
        counterTop.entrySet().stream().filter(e -> e.getValue() > 5_000)
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue()));
    }


    void act(Sample s) {
        Set<String> seenDomains = new HashSet<>();
        Set<String> seenTops = new HashSet<>();

        seenDomains.add(s.url.domain.toString());

        for (var request : s.requests) {
            String host = request.url.getHost();
            if (seenDomains.add(host)) {
                counter.merge(host, 1, Integer::sum);
            }
            try {
                String top = new EdgeUrl(request.url).domain.topDomain;
                if (seenTops.add(top)) {
                    counterTop.merge(top, 1, Integer::sum);
                }
            }
            catch (Exception ex) {
                //
            }
        }

    }
}
