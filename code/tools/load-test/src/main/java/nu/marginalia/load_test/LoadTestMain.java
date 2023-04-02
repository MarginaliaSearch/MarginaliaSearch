package nu.marginalia.load_test;

import nu.marginalia.WmsaHome;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class LoadTestMain {
    private static List<String> commonWords;
    public static void main(String... args) throws URISyntaxException, IOException, InterruptedException {
        commonWords = loadCommonWords();


        System.out.println(commonWords.size());

        HttpClient client = HttpClient.newHttpClient();

        List<Long> times = new ArrayList<>();

        for (int i = 0; i < 10000; i++) {
            String uri = "http://127.0.0.1:8080/search?query=%s&profile=corpo".formatted(
                    Strings.join(pickNCommonWords(2), '+')
            );

            HttpRequest req = HttpRequest.newBuilder(new URI(uri))
                    .build();


            long startTime = System.currentTimeMillis();

            client.send(req, HttpResponse.BodyHandlers.ofString());

            long stopTime = System.currentTimeMillis();

            times.add(stopTime - startTime);
            if (times.size() > 100) {
                System.out.println(times.stream().mapToLong(Long::longValue).average().orElse(-1));
                times.clear();
            }
        }
    }

    private static List<String> loadCommonWords() {
        var dict = new TermFrequencyDict(WmsaHome.getLanguageModels());


        try (var lines = Files.lines(Path.of("/usr/share/dict/american-english"))) {
            return lines.map(String::toLowerCase).filter(term -> dict.getTermFreq(term) > 100000).collect(Collectors.toList());
        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    static List<String> pickNCommonWords(int n) {
        assert commonWords.size() > 10*n;

        Set<String> words = new HashSet<>(n);
        Random r = new Random(System.nanoTime());
        while (words.size() < n) {
            words.add(commonWords.get(r.nextInt(0, commonWords.size())));
        }

        return new ArrayList<>(words);
    }
}
