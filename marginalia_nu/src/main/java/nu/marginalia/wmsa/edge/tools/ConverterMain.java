package nu.marginalia.wmsa.edge.tools;


import com.zaxxer.hikari.HikariDataSource;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.configuration.module.DatabaseModule;
import nu.marginalia.wmsa.edge.archive.archiver.ArchiveExtractor;
import nu.marginalia.wmsa.edge.assistant.dict.NGramDict;
import nu.marginalia.wmsa.edge.crawler.domain.LinkParser;
import nu.marginalia.wmsa.edge.crawler.domain.language.LanguageFilter;
import nu.marginalia.wmsa.edge.crawler.domain.language.WordPatterns;
import nu.marginalia.wmsa.edge.crawler.domain.language.conf.LanguageModels;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.DocumentKeywordExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.language.processing.SentenceExtractor;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlFeature;
import nu.marginalia.wmsa.edge.crawler.domain.processor.HtmlStandardExtractor;
import nu.marginalia.wmsa.edge.index.model.IndexBlock;
import nu.marginalia.wmsa.edge.index.service.dictionary.DictionaryWriter;
import nu.marginalia.wmsa.edge.index.service.index.SearchIndexWriterImpl;
import nu.marginalia.wmsa.edge.model.*;
import nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard;
import nu.marginalia.wmsa.edge.model.crawl.EdgePageWordSet;
import nu.marginalia.wmsa.edge.model.crawl.EdgeRawPageContents;
import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.mariadb.jdbc.Driver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import static nu.marginalia.wmsa.edge.model.crawl.EdgeHtmlStandard.UNKNOWN;

public class ConverterMain {
    static LinkedBlockingQueue<EdgeRawPageContents> processQueue = new LinkedBlockingQueue<>(20);
    static LinkedBlockingQueue<UploadJob> uploadQueue = new LinkedBlockingQueue<>(2);

    static TObjectIntHashMap<String> urlToIdMap = new TObjectIntHashMap<>(50_000_000, 0.5f, -1);
    static TObjectIntHashMap<String> domainToIdMap = new TObjectIntHashMap<>(5_000_000, 0.5f, -1);
    static TIntObjectHashMap<String> idToDomainMap = new TIntObjectHashMap<>(5_000_000, 0.5f, -1);
    static HikariDataSource conn;

    private static SearchIndexWriterImpl indexWriter;
    private static DictionaryWriter dictionaryWriter;

    @AllArgsConstructor
    static class UploadJob {
        EdgeId<EdgeDomain> domainId;
        EdgeId<EdgeUrl> urlId;
        EdgePageWordSet words;
        int wordCount;
    };
    static volatile boolean running = true;

    public static void main(String... args) throws IOException {
        org.mariadb.jdbc.Driver driver = new Driver();

        dictionaryWriter = new DictionaryWriter(new File(args[0]), 1L << 30, true);
        indexWriter = new SearchIndexWriterImpl(dictionaryWriter, new File(args[1]));

        new Thread(ConverterMain::uploadThread, "Uploader").start();

        for (int i = 0; i < 24; i++) {
            new Thread(ConverterMain::processorThread, "Processor-"+i).start();
        }

        conn = new DatabaseModule().provideConnection();

        System.out.println("Loading URLs and domains");
        try (var c = conn.getConnection();
             var getUrlsStmt = c.prepareStatement("SELECT EC_URL.ID, DOMAIN_ID, PROTO, URL FROM EC_URL WHERE VISITED");
             var getDomainsStmt = c.prepareStatement("SELECT ID, URL_PART FROM EC_DOMAIN WHERE INDEXED>0")
        ) {
            getUrlsStmt.setFetchSize(10_000);
            getDomainsStmt.setFetchSize(10_000);

            System.out.println("Fetch domains");
            var domainRsp = getDomainsStmt.executeQuery();
            while (domainRsp.next()) {
                domainToIdMap.put(domainRsp.getString(2), domainRsp.getInt(1));
                idToDomainMap.put(domainRsp.getInt(1), domainRsp.getString(2));
            }

            System.out.println("Fetch URLs");
            var urlRsp = getUrlsStmt.executeQuery();
            while (urlRsp.next()) {
                String urlStr = urlRsp.getString(3) + "://" + idToDomainMap.get(urlRsp.getInt(2)) + urlRsp.getString(4);
                urlToIdMap.put(urlStr, urlRsp.getInt(1));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }

//        new Thread(ConverterMain::uploadThread, "Uploader").start();
//
//        for (int i = 0; i < 24; i++) {
//            new Thread(ConverterMain::processorThread, "Processor-"+i).start();
//        }

        System.out.println("Loaded URLs and domains");

        new ArchiveExtractor(Path.of(args[2])).forEach(
                page -> {
                    if (page.contentType.contentType.startsWith("application/xhtml")
                            || page.contentType.contentType.startsWith("text/html")) {
                        try {
                            int domainId = domainToIdMap.get(page.url.domain.toString());
                            if (domainId >= 0 && page.redirectUrl == null) {
                                int urlId = urlToIdMap.get(page.url.toString());
                                int dataHash = page.data.hashCode();
                                try (var c = conn.getConnection();
                                    var updateHash = c.prepareStatement("UPDATE EC_URL SET DATA_HASH=? WHERE ID=?"))
                                {
                                    updateHash.setInt(1, dataHash);
                                    updateHash.setInt(2, urlId);
                                    updateHash.executeUpdate();
                                }
                                catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

        running = false;
    }

    static LanguageModels lm = new LanguageModels(
            Path.of("/var/lib/wmsa/model/ngrams-generous-emstr.bin"),
            Path.of("/var/lib/wmsa/model/tfreq-new-algo3.bin"),
            Path.of("/var/lib/wmsa/model/opennlp-sentence.bin"),
            Path.of("/var/lib/wmsa/model/English.RDR"),
            Path.of("/var/lib/wmsa/model/English.DICT"),
            Path.of("/var/lib/wmsa/model/opennlp-tok.bin")
    );
    static NGramDict dict = new NGramDict(lm);

    private static final LanguageFilter languageFilter = new LanguageFilter();
    private static final LinkParser linkParser = new LinkParser();
    public static void processorThread() {
        SentenceExtractor newSe = new SentenceExtractor(lm);
        DocumentKeywordExtractor documentKeywordExtractor = new DocumentKeywordExtractor(dict);

        try {
            while (running || !processQueue.isEmpty()) {
                var job = processQueue.take();
                if (job.data.length() > 512*1024) {
                    System.out.println(job.url + " too big, skipping");
                }

                var parsed = Jsoup.parse(job.data);
                var text = parsed.text();

                if (languageFilter.isBlockedUnicodeRange(text)) {
                    continue;
                }

                var dld = newSe.extractSentences(parsed.clone());
                var keywords = documentKeywordExtractor.extractKeywords(dld);
                int wc = dld.totalNumWords();

                if (wc > 100) {
                    double languageAgreement = languageFilter.dictionaryAgreement(dld);
                    if (languageAgreement < 0.05) {
                        continue;
                    }
                }


                EdgeHtmlStandard htmlStandard = HtmlStandardExtractor.parseDocType(parsed.documentType());
                if (UNKNOWN.equals(htmlStandard)) {
                    htmlStandard = HtmlStandardExtractor.sniffHtmlStandard(parsed);
                }

                int scriptTags = getScriptPenalty(parsed);
                var featureSet = getFeatureSet(parsed, scriptTags, job.hasCookies);
                addTags(keywords, htmlStandard, job.url, featureSet);

                extractLinkWords(keywords, job.getUrl(), parsed);

                uploadQueue.put(new UploadJob(
                        new EdgeId<>(domainToIdMap.get(job.url.domain.toString())),
                        new EdgeId<>(urlToIdMap.get(job.url.toString())),
                        keywords,
                        0
                ));

            }
        }
        catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }


    private static Map<EdgeUrl, Set<String>> extractLinkWords(EdgePageWordSet keywords, EdgeUrl pageUrl, Document parsed) {

        List<Pair<EdgeUrl, String>> urls = new ArrayList<>();
        Set<String> linkKeywords = new HashSet<>();
        Map<EdgeUrl, Set<String>> linkTextWords = new ConcurrentHashMap<>();

        for (var tag : parsed.getElementsByTag("a")) {
            if (!tag.hasAttr("href")) {
                continue;
            }
            if (urls.size() > 100) {
                break;
            }

            var linkOpt = linkParser.parseLink(pageUrl, tag);
            if (linkOpt.isEmpty())
                continue;

            var link = linkOpt.get();

            urls.add(Pair.of(link, tag.text()));

            if (!Objects.equals(link.domain.domain, pageUrl.domain.domain)
                    && linkKeywords.size() <= 25)
            {
                linkKeywords.add("links:" + link.domain.domain);
            }
//
//            Set<String> words = new HashSet<>();
//
//            for (var sent : sentenceExtractor.extractSentencesFromString(tag.text())) {
//                for (var keyword : keywordExtractor.getWordsFromSentence(sent)) {
//                    words.add(sent.constructWordFromSpan(keyword));
//                }
//            }
//
//            linkTextWords.compute(link, (k, set) -> {
//                if (set == null) return words;
//                else { set.addAll(words); return set; }
//            });

        }

        keywords.get(IndexBlock.Meta).addAll(linkKeywords);

        if (WordPatterns.wordQualitiesPredicate.test(pageUrl.domain.domain.toLowerCase())) {
            keywords.get(IndexBlock.Link).addJust(pageUrl.domain.domain.toLowerCase());
        }

        return linkTextWords;
    }

    private static int getScriptPenalty(Document parsed) {
        var scriptTags = parsed.getElementsByTag("script");
        String scriptText = scriptTags.html();
        int badScript = 0;
        if (scriptText.contains(".createElement(")) {
            badScript = 1;
        }
        return scriptTags.size() + badScript + (scriptText.length())/1000;
    }

    static List<String> trackers = List.of("adform.net",
            "connect.facebook",
            "googletagmanager.com",
            "googlesyndication.com",
            "google.com",
            "twitter.com",
            "smartadserver.com",
            "doubleclick.com",
            "2mdn.com",
            "dmtry.com",
            "bing.com",
            "msn.com",
            "amazon-adsystem.com",
            "alexametrics.com",
            "rubiconproject.com",
            "chango.com",
            "d5nxst8fruw4z.cloudfront.net",
            "d31qbv1cthcecs.cloudfront.net",
            "linkedin.com");

    private static Set<HtmlFeature> getFeatureSet(Document parsed, int scriptTags, boolean cookies) {
        Set<HtmlFeature> features = new HashSet<>();

        if (scriptTags > 0) {
            features.add(HtmlFeature.JS);
        }
        if (!parsed.getElementsByTag("object").isEmpty()
                || !parsed.getElementsByTag("audio").isEmpty()
                || !parsed.getElementsByTag("video").isEmpty()) {
            features.add(HtmlFeature.MEDIA);
        }
        if (parsed.getElementsByTag("script").stream()
                .filter(tag -> tag.attr("src") != null)
                .anyMatch(tag -> trackers.stream().anyMatch(tracker -> tag.attr("src").contains(tracker)))) {
            features.add(HtmlFeature.TRACKING);
        }
        if (parsed.getElementsByTag("script").html().contains("google-analytics.com")) {
            features.add(HtmlFeature.TRACKING);
        }
        if (parsed.getElementsByTag("a").stream().map(e -> e.attr("href"))
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .anyMatch(href ->
                        href.contains("amzn.to/") || href.contains("amazon.com/"))) {
            features.add(HtmlFeature.AFFILIATE_LINK);
        }
        if (cookies) {
            features.add(HtmlFeature.COOKIES);
        }

        return features;
    }

    private static void addTags(EdgePageWordSet wordSet, EdgeHtmlStandard htmlStandard, EdgeUrl url, Set<HtmlFeature> features) {
        List<String> tagWords = new ArrayList<>();
        tagWords.add("format:"+htmlStandard.toString().toLowerCase());
        tagWords.add("site:"+url.domain.toString().toLowerCase());
        tagWords.add("proto:"+url.proto.toLowerCase());
        tagWords.add("js:" + Boolean.toString(features.contains(HtmlFeature.JS)).toLowerCase());
        if (features.contains(HtmlFeature.MEDIA)) {
            tagWords.add("special:media");
        }
        if (features.contains(HtmlFeature.TRACKING)) {
            tagWords.add("special:tracking");
        }
        if (features.contains(HtmlFeature.AFFILIATE_LINK)) {
            tagWords.add("special:affiliate");
        }
        if (features.contains(HtmlFeature.COOKIES)) {
            tagWords.add("special:cookies");
        }
        wordSet.append(IndexBlock.Meta, tagWords);
        wordSet.append(IndexBlock.Words, tagWords);
    }

    @SneakyThrows
    public static void uploadThread() {

        while (running || !processQueue.isEmpty() || !uploadQueue.isEmpty()) {
            var data = uploadQueue.take();

            if (!data.words.isEmpty()) {
                for (var words : data.words.values()) {
                    if (!words.getWords().isEmpty()) {
                        if (words.size() < 1000) {
                            indexWriter.put(data.domainId, data.urlId, words.block, words.words);
                        } else {
                            chunks(words.words, 1000).forEach(chunk -> {
                                indexWriter.put(data.domainId, data.urlId, words.block, chunk);
                            });
                        }
                    }
                }
            }
        }

        System.out.println("Closing");
        dictionaryWriter.commitToDisk();
        indexWriter.forceWrite();
        dictionaryWriter.close();
        indexWriter.close();
        System.out.println("Done");
    }

    private static <T> List<List<T>> chunks(Collection<T> coll, int size) {
        List<List<T>> ret = new ArrayList<>();
        List<T> data = List.copyOf(coll);

        for (int i = 0; i < data.size(); i+=size) {
            ret.add(data.subList(i, Math.min(data.size(), i+size)));
        }

        return ret;
    }

}
