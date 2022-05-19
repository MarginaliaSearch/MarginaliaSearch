package nu.marginalia.wmsa.edge.tools;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.edge.archive.client.ArchiveClient;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.assistant.dict.WikiCleaner;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import org.jsoup.Jsoup;
import org.openzim.ZIMTypes.ZIMFile;
import org.openzim.ZIMTypes.ZIMReader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ZimConverterMain {

    static LinkedBlockingQueue<ConversionJob> jobQueue = new LinkedBlockingQueue<>(100);
    static LinkedBlockingQueue<String> analysisQueue = new LinkedBlockingQueue<>(100);
    static boolean hasData = true;
    static ArchiveClient archiveClient = new ArchiveClient();
    static NGramDict dict = new NGramDict(new LanguageModels(
            Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
            Path.of("/var/lib/wmsa/model/tfreq-generous-emstr.bin"),
            Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
            Path.of("/var/lib/wmsa/model/English.RDR"),
            Path.of("/var/lib/wmsa/model/English.DICT"),
            Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
    )
    );
    public void extractUrlList() throws IOException {
        var zr = new ZIMReader(new ZIMFile("/home/vlofgren/Work/wikipedia_en_all_nopic_2021-01.zim"));

        var urlList = zr.getURLListByURL();

        try (PrintWriter pw = new PrintWriter(new FileOutputStream("/home/vlofgren/Work/wikiTitlesAndRedirects.sql"))) {
            zr.forEachTitles(
                    ae -> {
                        pw.printf("INSERT INTO REF_WIKI_TITLE(NAME) VALUES (\"%s\");\n", ae.getUrl().replace("\\", "\\\\").replace("\"", "\\\""));
                    },
                    re -> {
                        pw.printf("INSERT INTO REF_WIKI_TITLE(NAME, REF_NAME) VALUES (\"%s\",\"%s\");\n", re.getUrl().replace("\\", "\\\\").replace("\"", "\\\""), urlList.get(re.getRedirectIndex()).replace("\\", "\\\\").replace("\"", "\\\""));
                    }
            );
        }
    }

    public static void main(String[] args) throws IOException {
//        convertJust("Aleph_number");
//        convertJust("Floyd–Steinberg_dithering");
//        convertJust("Laplace's_equation");
//        convertJust("John_Fahey");
//        convertJust("Plotinus");
//        convertJust("C++");
        convertAll(args);
        archiveClient.close();
    }

    @SneakyThrows
    private static void convertJust(String url) {
        String newData = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/" + url,
                Files.readString(Path.of("/home/vlofgren/Work/wiki-convert/", "in-" + url + ".html")));
        Files.writeString(Path.of("/home/vlofgren/Work/wiki-convert/", "out-" + url + ".html"), newData);
    }

    private static void extractOne(String which, int clusterId) throws IOException {
//        var zr = new ZIMReader(new ZIMFile(args[1]));
        var zr = new ZIMReader(new ZIMFile("/home/vlofgren/Work/wikipedia_en_all_nopic_2021-01.zim"));

        int[] cluster = new int[] { clusterId };
        if (clusterId == -1) {
            zr.forEachTitles(ae -> {
                if (ae.getUrl().equals(which)) {
                    System.err.print(ae.getUrl() + " " + ae.getClusterNumber());
                    cluster[0] = ae.getClusterNumber();
                }
            }, re -> {
            });
        }

        System.err.println("Extracting cluster " + cluster[0] );
        if (cluster[0] == -1) {
            return;
        }
        zr.forEachArticles((url, art) -> {
            if (art != null) {
                if (which.equals(url)) {
                    try {
                        Files.writeString(Path.of("/home/vlofgren/Work/wiki-convert/","in-" + url + ".html"), art);
                        String newData = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/" + url, art);
                        Files.writeString(Path.of("/home/vlofgren/Work/wiki-convert/", "out-" + url + ".html"), newData);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
                scheduleJob(url, art);
            }
        }, p -> p == cluster[0]);

    }

    private static void convertAll(String[] args) throws IOException {
        archiveClient.setServiceRoute("127.0.0.1", Integer.parseInt(args[0]));
        var zr = new ZIMReader(new ZIMFile(args[1]));
//        var zr = new ZIMReader(new ZIMFile("/home/vlofgren/Work/wikipedia_en_all_nopic_2021-01.zim"));

        for (int i = 0; i < 8; i++) {
            Thread t = new Thread(ZimConverterMain::jobExecutor);
            t.setName("Converter");
            t.start();

            Thread t2 = new Thread(() -> {
                for (; ; ) {
                    String pt;
                    try {
                        pt = analysisQueue.take();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    }
//                    var topic = new TopicWordExtractor().extractWords(pt);
//                    var words = new NGramTextRankExtractor(dict, topic).extractWords(Collections.emptyList(), pt);
//                    System.out.println(Strings.join(words, ','));
                }
            });
            t2.setName("Analysis");
            t2.start();
        }

        zr.forEachArticles((url, art) -> {
           if (art != null) {
               scheduleJob(url, art);
           }
       }, p -> true);

        hasData = false;
        archiveClient.close();
    }

    @SneakyThrows
    private static void jobExecutor() {
        while (hasData || !jobQueue.isEmpty()) {
            var job = jobQueue.take();
            try {
                job.convert();
            }
            catch (Exception ex) {
                System.err.println("Error in " + job.url);
                ex.printStackTrace();
            }
        }
    }

    @SneakyThrows
    private static void scheduleJob(String url, String art) {
        jobQueue.put(new ConversionJob(art, url));
    }

    static Map<Long, Integer> wordCount = new ConcurrentHashMap<>();
    static boolean isKeyword(String word) {

        int limit = 100_000;
        long n = word.chars().filter(c -> c=='_').count();
        if (n == 0) limit = 2;
        if (n == 1) limit = 1;
        if (n == 2) limit = 1;
        if (n >= 3) limit = 1;

        long c = word.chars().filter(ch -> ch >= 'a' && ch <= 'z').count();
        if (c-2 <= n) {
            return false;
        }
        int hashA = word.hashCode();
        int hashB = Objects.hash(n, c, word.length(), word.charAt(0));
        long hash = (long) hashA + ((long) hashB << 32);

        return wordCount.compute(hash, (k, v) -> v == null ? 1 : v+1) == limit;
    }
    @AllArgsConstructor
    private static class ConversionJob {
        private final String data;
        private final String url;


        public void convert() throws IOException, InterruptedException {
            var page = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/" + url, data);
            String pt = Jsoup.parse(page).text();
            analysisQueue.put(pt);

            /*

            String newData = new WikiCleaner().cleanWikiJunk("https://en.wikipedia.org/wiki/" + url, data);


            if (null != newData) {
                archiveClient.submitWiki(Context.internal(), url, newData)
                        .retry(5)
                        .blockingSubscribe();

            }*/
        }
    }
}
