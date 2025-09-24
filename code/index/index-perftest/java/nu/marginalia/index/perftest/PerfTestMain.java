package nu.marginalia.index.perftest;

import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.WmsaHome;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.functions.searchquery.QueryFactory;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.index.CombinedIndexReader;
import nu.marginalia.index.IndexQueryExecution;
import nu.marginalia.index.StatefulIndex;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.model.CombinedDocIdList;
import nu.marginalia.index.model.SearchContext;
import nu.marginalia.index.results.DomainRankingOverrides;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.reverse.FullReverseIndexReader;
import nu.marginalia.index.reverse.PrioReverseIndexReader;
import nu.marginalia.index.reverse.WordLexicon;
import nu.marginalia.index.reverse.query.IndexQuery;
import nu.marginalia.index.searchset.SearchSetAny;
import nu.marginalia.language.config.LanguageConfigLocation;
import nu.marginalia.language.config.LanguageConfiguration;
import nu.marginalia.language.keywords.KeywordHasher;
import nu.marginalia.linkdb.docs.DocumentDbReader;
import nu.marginalia.segmentation.NgramLexicon;
import nu.marginalia.term_frequency_dict.TermFrequencyDict;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PerfTestMain {
    static Duration warmupTime = Duration.ofMinutes(1);
    static Duration runTime = Duration.ofMinutes(10);

    public static void main(String[] args) {
        if (args.length != 4) {
            System.err.println("Arguments: home-dir index-dir query");
            System.exit(255);
        }

        try {
            Path indexDir = Paths.get(args[0]);
            if (!Files.isDirectory(indexDir)) {
                System.err.println("Index directory is not a directory");
                System.exit(255);
            }
            Path homeDir = Paths.get(args[1]);
            String scenario = args[2];
            String query = args[3];

            switch (scenario) {
                case "valuation" -> runValuation(indexDir, homeDir, query);
                case "lookup" -> runLookup(indexDir, homeDir, query);
                case "execution" -> runExecution(indexDir, homeDir, query);
            }

            System.exit(0);
        }
        catch (NumberFormatException e) {
            System.err.println("Arguments: data-dir index-dir query");
            System.exit(255);
        }
        catch (Exception ex) {
            System.err.println("Error during testing");
            ex.printStackTrace();
            System.exit(255);
        }
        System.out.println(Arrays.toString(args));
    }

    private static CombinedIndexReader createCombinedIndexReader(Path indexDir) throws IOException {

        return new CombinedIndexReader(
                new ForwardIndexReader(
                        indexDir.resolve("ir/fwd-doc-id.dat"),
                        indexDir.resolve("ir/fwd-doc-data.dat"),
                        indexDir.resolve("ir/fwd-spans.dat")
                ),
                new FullReverseIndexReader(
                        "full",
                        List.of(new WordLexicon("en", indexDir.resolve("ir/rev-words-en.dat"))),
                        indexDir.resolve("ir/rev-docs.dat"),
                        indexDir.resolve("ir/rev-positions.dat")
                ),
                new PrioReverseIndexReader(
                        "prio",
                        List.of(new WordLexicon("en", indexDir.resolve("ir/rev-words-prio-en.dat"))),
                        indexDir.resolve("ir/rev-prio-docs.dat")
                )
        );
    }

    private static IndexResultRankingService createIndexResultRankingService(Path indexDir, CombinedIndexReader combinedIndexReader) throws IOException, SQLException {
        return new IndexResultRankingService(
                new DocumentDbReader(indexDir.resolve("ldbr/documents.db")),
                new StatefulIndex(combinedIndexReader),
                new DomainRankingOverrides(null, Path.of("xxxx"))
        );
    }

    static QueryFactory createQueryFactory(Path homeDir) throws Exception {
        return new QueryFactory(
                new QueryExpansion(
                        new TermFrequencyDict(homeDir.resolve("model/tfreq-new-algo3.bin")),
                        new NgramLexicon()
                ),
                new LanguageConfiguration(WmsaHome.getLanguageModels(), new LanguageConfigLocation.Experimental())
        );
    }

    public static void runValuation(Path homeDir,
                                    Path indexDir,
                                    String rawQuery) throws Exception {

        CombinedIndexReader indexReader = createCombinedIndexReader(indexDir);
        QueryFactory queryFactory = createQueryFactory(homeDir);
        IndexResultRankingService rankingService = createIndexResultRankingService(indexDir, indexReader);

        var queryLimits = RpcQueryLimits.newBuilder()
                .setTimeoutMs(10_000)
                .setResultsTotal(1000)
                .setResultsByDomain(10)
                .setFetchSize(4096)
                .build();
        SearchSpecification parsedQuery = queryFactory.createQuery(new QueryParams(rawQuery, queryLimits, "NONE", NsfwFilterTier.OFF, "en"), PrototypeRankingParameters.sensibleDefaults()).specs;

        System.out.println("Query compiled to: " + parsedQuery.query.compiledQuery);

        var rankingContext = SearchContext.create(indexReader, new KeywordHasher.AsciiIsh(), parsedQuery, new SearchSetAny());
        List<IndexQuery> queries = indexReader.createQueries(rankingContext);

        TLongArrayList allResults = new TLongArrayList();
        LongQueryBuffer buffer = new LongQueryBuffer(512);

        for (var query : queries) {
            while (query.hasMore() && allResults.size() < 512 ) {
                query.getMoreResults(buffer);
                allResults.addAll(buffer.copyData());
            }
            if (allResults.size() >= 512)
                break;
        }
        allResults.sort();
        if (allResults.size() > 512) {
            allResults.subList(512,  allResults.size()).clear();
        }

        var rankingData = rankingService.prepareRankingData(rankingContext, new CombinedDocIdList(allResults.toArray()));

        int sum = 0;

        Instant runEndTime = Instant.now().plus(runTime);
        Instant runStartTime =  Instant.now();
        int sum2 = 0;
        List<Double> times = new ArrayList<>();

        int iter;
        for (iter = 0;; iter++) {
            long start = System.nanoTime();
            sum2 += rankingService.rankResults(rankingContext, rankingData).size();
            long end = System.nanoTime();
            times.add((end - start)/1_000_000.);

            if ((iter % 100) == 0) {
                if (Instant.now().isAfter(runEndTime)) {
                    break;
                }
                if (times.size() > 100) {
                    double[] timesSample = times.stream().mapToDouble(Double::doubleValue).skip(times.size() - 100).sorted().toArray();
                    System.out.format("P1: %f P10: %f, P90: %f, P99: %f\n", timesSample[1], timesSample[10], timesSample[90], timesSample[99]);
                }
                System.out.println(Duration.between(runStartTime, Instant.now()).toMillis() / 1000. + " best times: " + (allResults.size() / 512.) *  times.stream().mapToDouble(Double::doubleValue).sorted().limit(3).average().orElse(-1));
            }
        }
        System.out.println("Benchmark complete after " + iter + " iters!");

        System.out.println("Best times: " + (allResults.size() / 512.) *  times.stream().mapToDouble(Double::doubleValue).sorted().limit(3).average().orElse(-1));
        System.out.println("Warmup sum: " + sum);
        System.out.println("Main sum: " + sum2);
        System.out.println(rankingData.size());
    }

    public static void runExecution(Path homeDir,
                                    Path indexDir,
                                    String rawQuery) throws Exception {

        CombinedIndexReader indexReader = createCombinedIndexReader(indexDir);
        QueryFactory queryFactory = createQueryFactory(homeDir);
        IndexResultRankingService rankingService = createIndexResultRankingService(indexDir, indexReader);

        var queryLimits = RpcQueryLimits.newBuilder()
                .setTimeoutMs(50)
                .setResultsTotal(1000)
                .setResultsByDomain(10)
                .setFetchSize(4096)
                .build();
        SearchSpecification parsedQuery = queryFactory.createQuery(new QueryParams(rawQuery, queryLimits, "NONE", NsfwFilterTier.OFF, "en"), PrototypeRankingParameters.sensibleDefaults()).specs;
        System.out.println("Query compiled to: " + parsedQuery.query.compiledQuery);

        System.out.println("Running warmup loop!");
        int sum = 0;

        Instant runEndTime = Instant.now().plus(runTime);
        Instant runStartTime =  Instant.now();
        int sum2 = 0;
        List<Double> rates = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        int iter;
        for (iter = 0;; iter++) {
            var execution = new IndexQueryExecution(indexReader, rankingService, SearchContext.create(indexReader, new KeywordHasher.AsciiIsh(), parsedQuery, new SearchSetAny()), 1);
            long start = System.nanoTime();
            execution.run();
            long end = System.nanoTime();
            sum2 += execution.itemsProcessed();
            rates.add(execution.itemsProcessed() / ((end - start)/1_000_000_000.));
            times.add((end - start)/1_000_000.);
            indexReader.reset();
            if ((iter % 100) == 0) {
                if (Instant.now().isAfter(runEndTime)) {
                    break;
                }
                if (times.size() > 100) {
                    double[] timesSample = times.stream().mapToDouble(Double::doubleValue).skip(times.size() - 100).sorted().toArray();
                    System.out.format("P1: %f P10: %f, P90: %f, P99: %f\n", timesSample[1], timesSample[10], timesSample[90], timesSample[99]);
                }
                System.out.println(Duration.between(runStartTime, Instant.now()).toMillis() / 1000. + " best rates: " +  rates.stream().mapToDouble(Double::doubleValue).map(i -> -i).sorted().map(i -> -i).limit(3).average().orElse(-1));
            }
        }

        System.out.println("Benchmark complete after " + iter + " iters!");
        System.out.println("Best counts: " + rates.stream().mapToDouble(Double::doubleValue).map(i -> -i).sorted().map(i -> -i).limit(3).average().orElse(-1));
        System.out.println("Warmup sum: " + sum);
        System.out.println("Main sum: " + sum2);
    }

    public static void runLookup(Path homeDir,
                                    Path indexDir,
                                    String rawQuery) throws Exception
    {

        CombinedIndexReader indexReader = createCombinedIndexReader(indexDir);
        QueryFactory queryFactory = createQueryFactory(homeDir);

        var queryLimits = RpcQueryLimits.newBuilder()
                .setTimeoutMs(10_000)
                .setResultsTotal(1000)
                .setResultsByDomain(10)
                .setFetchSize(4096)
                .build();
        SearchSpecification parsedQuery = queryFactory.createQuery(new QueryParams(rawQuery, queryLimits, "NONE", NsfwFilterTier.OFF, "en"), PrototypeRankingParameters.sensibleDefaults()).specs;

        System.out.println("Query compiled to: " + parsedQuery.query.compiledQuery);

        SearchContext searchContext = SearchContext.create(indexReader, new KeywordHasher.AsciiIsh(), parsedQuery, new SearchSetAny());


        Instant runEndTime = Instant.now().plus(runTime);

        LongQueryBuffer buffer = new LongQueryBuffer(512);
        int sum1 = 0;
        int iter;

        Instant runStartTime =  Instant.now();
        int sum2 = 0;
        List<Double> times = new ArrayList<>();
        for (iter = 0;; iter++) {
            indexReader.reset();
            List<IndexQuery> queries = indexReader.createQueries(searchContext);

            long start = System.nanoTime();
            for (var query : queries) {
                while (query.hasMore()) {
                    query.getMoreResults(buffer);
                    sum1 += buffer.end;
                    buffer.reset();
                }
            }
            long end = System.nanoTime();
            times.add((end - start)/1_000_000_000.);

            if ((iter % 10) == 0) {
                if (Instant.now().isAfter(runEndTime)) {
                    break;
                }
                if (times.size() > 100) {
                    double[] timesSample = times.stream().mapToDouble(Double::doubleValue).skip(times.size() - 100).sorted().toArray();
                    System.out.format("P1: %f P10: %f, P90: %f, P99: %f\n", timesSample[1], timesSample[10], timesSample[90], timesSample[99]);
                }
                System.out.println(Duration.between(runStartTime, Instant.now()).toMillis() / 1000. + " best times: " + times.stream().mapToDouble(Double::doubleValue).sorted().limit(3).average().orElse(-1));
            }
        }
        System.out.println("Benchmark complete after " + iter + " iters!");
        System.out.println("Best times: " + times.stream().mapToDouble(Double::doubleValue).sorted().limit(3).average().orElse(-1));
        System.out.println("Warmup sum: " + sum1);
        System.out.println("Main sum: " + sum2);
    }
}
