package nu.marginalia.wmsa.edge.crawler.domain.language.processing;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.SneakyThrows;
import nu.marginalia.util.TestLanguageModels;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.WordRep;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.WordSpan;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.model.tag.WordSeparator;
import nu.marginalia.wmsa.edge.index.service.util.ranking.BuggyReversePageRank;
import nu.marginalia.wmsa.edge.index.service.util.ranking.BuggyStandardPageRank;
import nu.marginalia.wmsa.edge.integration.wikipedia.WikipediaReader;
import nu.marginalia.wmsa.edge.model.EdgeDomain;
import nu.marginalia.wmsa.edge.model.EdgeUrl;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

class SentenceExtractorTest {
    SentenceExtractor newSe;
    SentenceExtractor legacySe;
    LanguageModels lm = TestLanguageModels.getLanguageModels();
    @BeforeEach
    public void setUp() {

        newSe = new SentenceExtractor(lm);
        legacySe = new SentenceExtractor(lm);
        legacySe.setLegacyMode(true);
    }


    @Test @Disabled
    public void getTheData() throws IOException {
        var connStr = "jdbc:mariadb://localhost:3306/WMSA_test?rewriteBatchedStatements=true";

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(connStr);
        config.setUsername("wmsa");
        config.setPassword("wmsa");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(100);
        config.setMinimumIdle(10);

        var conn = new HikariDataSource(config);

        var rpr = new BuggyReversePageRank(conn, "virginia.xroads.edu");
        var spr = new BuggyStandardPageRank(conn, "virginia.xroads.edu");

        var rankVector = spr.pageRankVector();
        var norm = rankVector.norm();

        int resultCount = rpr.size()/10;
        var domains = spr.pageRank(i -> rankVector.get(i) / norm, resultCount).toArray();
        int i = 0;

        try (var bw = Files.newBufferedWriter(Path.of("/tmp/domains.txt"));
                var stmt = conn.getConnection().prepareStatement("SELECT URL_PROTO, URL_DOMAIN, URL_PORT, URL_PATH FROM EC_URL_VIEW WHERE DOMAIN_ID=? AND TITLE IS NOT NULL ORDER BY ID ASC LIMIT 10 ")) {
            for (int domainId : domains) {
                bw.write(String.format("%f\n", i++/(double) resultCount));
                stmt.setInt(1, domainId);
                var rsp = stmt.executeQuery();
                while (rsp.next()) {
                    var url = new EdgeUrl(rsp.getString(1), new EdgeDomain(rsp.getString(2)),
                            rsp.getInt(3), rsp.getString(4));
                    bw.write(url.toString());
                    bw.write("\n");
                }
                bw.write(".\n");
            }

        }
        catch (Exception e) {

        }
    }

    @SneakyThrows
    @Test
    void testExtractSubject() {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");

        System.out.println("Running");

        var dict = new NGramDict(lm);

        SentenceExtractor se = new SentenceExtractor(lm);
        KeywordExtractor keywordExtractor = new KeywordExtractor();

        for (var file : Objects.requireNonNull(data.toFile().listFiles())) {
            System.out.println(file);
            var dld = se.extractSentences(Jsoup.parse(Files.readString(file.toPath())));
            Map<String, Integer> counts = new HashMap<>();
            for (var sentence : dld.sentences) {
                for (WordSpan kw : keywordExtractor.getNames(sentence)) {
                    if (kw.end + 2 >= sentence.length()) {
                        continue;
                    }
                    if (sentence.separators[kw.end] == WordSeparator.COMMA
                            || sentence.separators[kw.end + 1] == WordSeparator.COMMA)
                        break;

                    if (("VBZ".equals(sentence.posTags[kw.end]) || "VBP".equals(sentence.posTags[kw.end]))
                            && ("DT".equals(sentence.posTags[kw.end + 1]) || "RB".equals(sentence.posTags[kw.end]) || sentence.posTags[kw.end].startsWith("VB"))
                    ) {
                        counts.merge(new WordRep(sentence, new WordSpan(kw.start, kw.end)).word, -1, Integer::sum);
                    }
                }
            }

            int best = counts.values().stream().mapToInt(Integer::valueOf).min().orElse(0);

            counts.entrySet().stream().sorted(Map.Entry.comparingByValue())
                    .filter(e -> e.getValue()<-2 && e.getValue()<best*0.75)
                    .forEach(System.out::println);
        }

    }

    @Test
    public void testWikipedia() throws InterruptedException {

        System.out.println("Running");

        var dict = new NGramDict(lm);

        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        var reader = new WikipediaReader("/home/vlofgren/Work/wikipedia_en_100_nopic_2021-06.zim", new EdgeDomain("encyclopedia.marginalia.nu"),
                post -> {

                    var newResult = newSe.extractSentences(Jsoup.parse(post.body));

                    var newRes = documentKeywordExtractor.extractKeywords(newResult);
                    System.out.println(newRes);
                });
        reader.join();
    }
    @Test
    void extractSentences() throws IOException {
        var data = Path.of("/home/vlofgren/Code/tmp-data/");

        System.out.println("Running");

        var dict = new NGramDict(lm);

        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

//        documentKeywordExtractorLegacy.setLegacy(true);

//        for (;;) {
            long st = System.currentTimeMillis();
            for (var file : Objects.requireNonNull(data.toFile().listFiles())) {


                var newResult = newSe.extractSentences(Jsoup.parse(Files.readString(file.toPath())));

                var newRes = documentKeywordExtractor.extractKeywords(newResult);


//                var legacyRes = documentKeywordExtractorLegacy.extractKeywords(newResult);
//
//                EdgePageWordSet difference = new EdgePageWordSet();
//                for (IndexBlock block : IndexBlock.values()) {

//                    var newWords = new HashSet<>(newRes.get(block).words);
//                    var oldWords = new HashSet<>(legacyRes.get(block).words);
//                    newWords.removeAll(oldWords);

//                    if (!newWords.isEmpty()) {
//                        difference.append(block, newWords);
//                    }
//                }
//                System.out.println(difference);
                System.out.println(newRes);
//                System.out.println("---");
            }
            System.out.println(System.currentTimeMillis() - st);
//        }

    }

    @SneakyThrows
    @Test
    @Disabled
    public void testSE() {
        var result = newSe.extractSentences(Jsoup.parse(new URL("https://memex.marginalia.nu/log/26-personalized-pagerank.gmi"), 10000));

        var dict = new NGramDict(lm);
        System.out.println(new DocumentKeywordExtractor(dict).extractKeywords(result));


//
//        var pke = new PositionKeywordExtractor(dict, new KeywordExtractor());
//        pke.count(result).stream().map(wr -> wr.word).distinct().forEach(System.out::println);
//        for (var sent : result.sentences) {
//            System.out.println(sent);
//        }

    }

    @Test
    public void separatorExtraction() {
        seprateExtractor("Cookies, cream and shoes");
        seprateExtractor("Cookies");
        seprateExtractor("");

    }

    Pattern p = Pattern.compile("([, ]+)");
    public void seprateExtractor(String sentence) {
        var matcher = p.matcher(sentence);

        Arrays.stream(p.split(sentence)).forEach(System.out::println);
        List<String> words = new ArrayList<>();
        List<String> separators = new ArrayList<>();

        int start = 0;
        int wordStart = 0;
        while (wordStart <= sentence.length()) {
            if (!matcher.find(wordStart)) {
                words.add(sentence.substring(wordStart));
                separators.add("S");
                break;
            }

            if (wordStart != matcher.start()) {
                words.add(sentence.substring(wordStart, matcher.start()));
                separators.add(sentence.substring(matcher.start(), matcher.end()).isBlank() ? "S" : "C");
            }
            wordStart = matcher.end();
        }

        System.out.println(words);
        System.out.println(separators);
    }
}