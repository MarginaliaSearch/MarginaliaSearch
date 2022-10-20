package nu.marginalia.wmsa.edge.crawling;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CrawlerTestMain {

    static Bucket rateLimiter60RPM;
    static List<String> successfullyFetched = new ArrayList<>();

    public static void main(String... args) {
        var refill = Refill.greedy(1, Duration.ofSeconds(1));

        var bw = Bandwidth.classic(10, refill);
        rateLimiter60RPM = Bucket.builder().addLimit(bw).build();

        Spark.port(8080);
        Spark.before(CrawlerTestMain::before);
        Spark.after(CrawlerTestMain::after);
        Spark.get("/rate-limit/", CrawlerTestMain::index);
        Spark.get("/rate-limit/:n", CrawlerTestMain::n);

        Spark.before("/rate-limit/:n", CrawlerTestMain::rateLimitRequest);
        Spark.before("/intermittent-error/:n", CrawlerTestMain::simulateRandomTimeouts);

        Spark.get("/intermittent-error/", CrawlerTestMain::index);
        Spark.get("/intermittent-error/:n", CrawlerTestMain::n);

    }

    private static void rateLimitRequest(Request request, Response response) {
        if (!rateLimiter60RPM.tryConsume(1)) {
            Spark.halt(429);
        }
    }

    private static void simulateRandomTimeouts(Request request, Response response) {
        if (Math.random() < 0.25) {
            System.out.println("Simulating error");
            Spark.halt(503);
        }
    }

    public static void before(Request request, Response response) {
        System.out.println(request.pathInfo());
        successfullyFetched.add(request.pathInfo());
    }
    public static void after(Request request, Response response) {
        if (response.status() < 300) {
            successfullyFetched.add(request.pathInfo());
        }
    }

    private static Object n(Request request, Response response) {

        int num = Integer.parseInt(request.params("n"));
        return """
                <html>
                  <head>
                    <title>Index</title>
                  <body>
                  <h1>Index</h1>
                """ +
                    String.format("<a href=\"%d\">Next</a>, <a href=\"%d\">Next 2</a>", num+1, num+2)

                +
                """
                   
                <p>
                Goddess, sing me the anger, of Achilles, Peleus’ son, that fatal anger that brought countless
                sorrows on the Greeks, and sent many valiant souls of warriors down to Hades, leaving their
                bodies as spoil for dogs and carrion birds: for thus was the will of Zeus brought to fulfilment.
                    
                Sing of it from the moment when Agamemnon, Atreus’ son, that king of men, parted in wrath from noble Achilles.
                Which of the gods set these two to quarrel? Apollo, the son of Leto and Zeus, angered by the king, brought an 
                evil plague on the army, so that the men were dying, for the son of Atreus had dishonoured Chryses the priest.
                He it was who came to the swift Achaean ships, to free his daughter, bringing a wealth of ransom, carrying a 
                golden staff adorned with the ribbons of far-striking Apollo, and called out to the Achaeans, above all to the
                two leaders of armies, those sons of Atreus: ‘Atreides, and all you bronze-greaved Achaeans, may the gods who
                live on Olympus grant you to sack Priam’s city, and sail back home in safety; but take this ransom, and free
                my darling child; show reverence for Zeus’s son, far-striking Apollo.’
                """;
    }

    private static Object index(Request request, Response response) {
        return """
                <html>
                  <head>
                    <title>Index</title>
                  <body>
                  <h1>Index</h1>
                    <a href="0">Next</a>
                <p>
                Goddess, sing me the anger, of Achilles, Peleus’ son, that fatal anger that brought countless
                sorrows on the Greeks, and sent many valiant souls of warriors down to Hades, leaving their
                bodies as spoil for dogs and carrion birds: for thus was the will of Zeus brought to fulfilment.
                    
                Sing of it from the moment when Agamemnon, Atreus’ son, that king of men, parted in wrath from noble Achilles.
                Which of the gods set these two to quarrel? Apollo, the son of Leto and Zeus, angered by the king, brought an 
                evil plague on the army, so that the men were dying, for the son of Atreus had dishonoured Chryses the priest.
                He it was who came to the swift Achaean ships, to free his daughter, bringing a wealth of ransom, carrying a 
                golden staff adorned with the ribbons of far-striking Apollo, and called out to the Achaeans, above all to the
                two leaders of armies, those sons of Atreus: ‘Atreides, and all you bronze-greaved Achaeans, may the gods who
                live on Olympus grant you to sack Priam’s city, and sail back home in safety; but take this ransom, and free
                my darling child; show reverence for Zeus’s son, far-striking Apollo.’
                """;
    }
}
