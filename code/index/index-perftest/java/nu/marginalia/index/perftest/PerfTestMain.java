package nu.marginalia.index.perftest;

import gnu.trove.list.array.TLongArrayList;
import nu.marginalia.api.searchquery.RpcQueryLimits;
import nu.marginalia.api.searchquery.model.query.NsfwFilterTier;
import nu.marginalia.api.searchquery.model.query.QueryParams;
import nu.marginalia.api.searchquery.model.query.SearchSpecification;
import nu.marginalia.api.searchquery.model.results.PrototypeRankingParameters;
import nu.marginalia.array.page.LongQueryBuffer;
import nu.marginalia.functions.searchquery.QueryFactory;
import nu.marginalia.functions.searchquery.query_parser.QueryExpansion;
import nu.marginalia.index.FullReverseIndexReader;
import nu.marginalia.index.IndexQueryExecution;
import nu.marginalia.index.PrioReverseIndexReader;
import nu.marginalia.index.forward.ForwardIndexReader;
import nu.marginalia.index.index.CombinedIndexReader;
import nu.marginalia.index.index.StatefulIndex;
import nu.marginalia.index.model.ResultRankingContext;
import nu.marginalia.index.model.SearchParameters;
import nu.marginalia.index.model.SearchTerms;
import nu.marginalia.index.positions.PositionsFileReader;
import nu.marginalia.index.query.IndexQuery;
import nu.marginalia.index.results.DomainRankingOverrides;
import nu.marginalia.index.results.IndexResultRankingService;
import nu.marginalia.index.results.model.ids.CombinedDocIdList;
import nu.marginalia.index.searchset.SearchSetAny;
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
                        indexDir.resolve("ir/rev-words.dat"),
                        indexDir.resolve("ir/rev-docs.dat"),
                        new PositionsFileReader(indexDir.resolve("ir/rev-positions.dat"))
                ),
                new PrioReverseIndexReader(
                        "prio",
                        indexDir.resolve("ir/rev-prio-words.dat"),
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

    static QueryFactory createQueryFactory(Path homeDir) throws IOException {
        return new QueryFactory(
                new QueryExpansion(
                        new TermFrequencyDict(homeDir.resolve("model/tfreq-new-algo3.bin")),
                        new NgramLexicon()
                )
        );
    }

    public static void runValuation(Path homeDir,
                                    Path indexDir,
                                    String rawQuery) throws IOException, SQLException
    {

        CombinedIndexReader indexReader = createCombinedIndexReader(indexDir);
        QueryFactory queryFactory = createQueryFactory(homeDir);
        IndexResultRankingService rankingService = createIndexResultRankingService(indexDir, indexReader);

        var queryLimits = RpcQueryLimits.newBuilder()
                .setTimeoutMs(10_000)
                .setResultsTotal(1000)
                .setResultsByDomain(10)
                .setFetchSize(4096)
                .build();
        SearchSpecification parsedQuery = queryFactory.createQuery(new QueryParams(rawQuery, queryLimits, "NONE", NsfwFilterTier.OFF), PrototypeRankingParameters.sensibleDefaults()).specs;

        System.out.println("Query compiled to: " + parsedQuery.query.compiledQuery);

        SearchParameters searchParameters = new SearchParameters(parsedQuery, new SearchSetAny());

        List<IndexQuery> queries = indexReader.createQueries(new SearchTerms(searchParameters.query, searchParameters.compiledQueryIds), searchParameters.queryParams);

        TLongArrayList allResults = new TLongArrayList();
        LongQueryBuffer buffer = new LongQueryBuffer(4096);

        for (var query : queries) {
            while (query.hasMore() && allResults.size() < 4096 ) {
                query.getMoreResults(buffer);
                allResults.addAll(buffer.copyData());
            }
            if (allResults.size() >= 4096)
                break;
        }
        allResults.sort();
        if (allResults.size() > 4096) {
            allResults.subList(4096,  allResults.size()).clear();
        }

        var docIds = new CombinedDocIdList(allResults.toArray());
        var rankingContext = ResultRankingContext.create(indexReader, searchParameters);

        System.out.println("Running warmup loop!");
        int sum = 0;

        Instant runEndTime = Instant.now().plus(warmupTime);

        int iter;
        for (iter = 0;; iter++) {
            sum += rankingService.rankResults(rankingContext, docIds, false).size();
            if ((iter % 100) == 0 && Instant.now().isAfter(runEndTime)) {
                break;
            }
        }
        System.out.println("Warmup complete after " + iter + " iters!");

        runEndTime = Instant.now().plus(runTime);
        Instant runStartTime =  Instant.now();
        int sum2 = 0;
        List<Double> times = new ArrayList<>();
        for (iter = 0;; iter++) {
            long start = System.nanoTime();
            sum2 += rankingService.rankResults(rankingContext, docIds, false).size();
            long end = System.nanoTime();
            times.add((end - start)/1_000_000.);

            if ((iter % 100) == 0) {
                if (Instant.now().isAfter(runEndTime)) {
                    break;
                }
                System.out.println(Duration.between(runStartTime, Instant.now()).toMillis() / 1000. + " best times: " + (allResults.size() / 4096.) *  times.stream().mapToDouble(Double::doubleValue).sorted().limit(3).average().orElse(-1));
            }
        }
        System.out.println("Benchmark complete after " + iter + " iters!");
        System.out.println("Best times: " + (allResults.size() / 4096.) *  times.stream().mapToDouble(Double::doubleValue).sorted().limit(3).average().orElse(-1));
        System.out.println("Warmup sum: " + sum);
        System.out.println("Main sum: " + sum2);
        System.out.println(docIds.size());
    }

    public static void runExecution(Path homeDir,
                                    Path indexDir,
                                    String rawQuery) throws IOException, SQLException, InterruptedException {

        CombinedIndexReader indexReader = createCombinedIndexReader(indexDir);
        QueryFactory queryFactory = createQueryFactory(homeDir);
        IndexResultRankingService rankingService = createIndexResultRankingService(indexDir, indexReader);

        var queryLimits = RpcQueryLimits.newBuilder()
                .setTimeoutMs(50)
                .setResultsTotal(1000)
                .setResultsByDomain(10)
                .setFetchSize(4096)
                .build();
        SearchSpecification parsedQuery = queryFactory.createQuery(new QueryParams(rawQuery, queryLimits, "NONE", NsfwFilterTier.OFF), PrototypeRankingParameters.sensibleDefaults()).specs;
        System.out.println("Query compiled to: " + parsedQuery.query.compiledQuery);

        System.out.println("Running warmup loop!");
        int sum = 0;

        Instant runEndTime = Instant.now().plus(warmupTime);

        int iter;
        for (iter = 0;; iter++) {
            SearchParameters searchParameters = new SearchParameters(parsedQuery, new SearchSetAny());
            var execution = new IndexQueryExecution(searchParameters, rankingService, indexReader);
            execution.run();
            sum += execution.itemsProcessed();
            if ((iter % 100) == 0 && Instant.now().isAfter(runEndTime)) {
                break;
            }
        }
        System.out.println("Warmup complete after " + iter + " iters!");

        runEndTime = Instant.now().plus(runTime);
        Instant runStartTime =  Instant.now();
        int sum2 = 0;
        List<Integer> counts = new ArrayList<>();
        for (iter = 0;; iter++) {
            SearchParameters searchParameters = new SearchParameters(parsedQuery, new SearchSetAny());
            var execution = new IndexQueryExecution(searchParameters, rankingService, indexReader);
            execution.run();
            sum2 += execution.itemsProcessed();
            counts.add(execution.itemsProcessed());

            if ((iter % 100) == 0) {
                if (Instant.now().isAfter(runEndTime)) {
                    break;
                }
                System.out.println(Duration.between(runStartTime, Instant.now()).toMillis() / 1000. + " best counts: " +  counts.stream().mapToInt(Integer::intValue).map(i -> -i).sorted().map(i -> -i).limit(3).average().orElse(-1));
            }
        }
        System.out.println("Benchmark complete after " + iter + " iters!");
        System.out.println("Best counts: " + counts.stream().mapToInt(Integer::intValue).map(i -> -i).sorted().map(i -> -i).limit(3).average().orElse(-1));
        System.out.println("Warmup sum: " + sum);
        System.out.println("Main sum: " + sum2);
    }

    public static void runLookup(Path homeDir,
                                    Path indexDir,
                                    String rawQuery) throws IOException, SQLException
    {

        CombinedIndexReader indexReader = createCombinedIndexReader(indexDir);
        QueryFactory queryFactory = createQueryFactory(homeDir);

        var queryLimits = RpcQueryLimits.newBuilder()
                .setTimeoutMs(10_000)
                .setResultsTotal(1000)
                .setResultsByDomain(10)
                .setFetchSize(4096)
                .build();
        SearchSpecification parsedQuery = queryFactory.createQuery(new QueryParams(rawQuery, queryLimits, "NONE", NsfwFilterTier.OFF), PrototypeRankingParameters.sensibleDefaults()).specs;

        System.out.println("Query compiled to: " + parsedQuery.query.compiledQuery);

        SearchParameters searchParameters = new SearchParameters(parsedQuery, new SearchSetAny());


        Instant runEndTime = Instant.now().plus(warmupTime);

        LongQueryBuffer buffer = new LongQueryBuffer(4096);
        int sum1 = 0;
        int iter;
        for (iter = 0;; iter++) {
            List<IndexQuery> queries = indexReader.createQueries(new SearchTerms(searchParameters.query, searchParameters.compiledQueryIds), searchParameters.queryParams);

            for (var query : queries) {
                while (query.hasMore()) {
                    query.getMoreResults(buffer);
                    sum1 += buffer.end;
                    buffer.reset();
                }
            }

            if ((iter % 100) == 0 && Instant.now().isAfter(runEndTime)) {
                break;
            }
        }

        System.out.println("Warmup complete after " + iter + " iters with sum1 = " + sum1);

        runEndTime = Instant.now().plus(runTime);
        Instant runStartTime =  Instant.now();
        int sum2 = 0;
        List<Double> times = new ArrayList<>();
        for (iter = 0;; iter++) {
            List<IndexQuery> queries = indexReader.createQueries(new SearchTerms(searchParameters.query, searchParameters.compiledQueryIds), searchParameters.queryParams);

            long start = System.nanoTime();
            for (var query : queries) {
                while (query.hasMore()) {
                    query.getMoreResults(buffer);
                    sum1 += buffer.end;
                    buffer.reset();
                }
            }
            long end = System.nanoTime();
            times.add((end - start)/1_000_000.);

            if ((iter % 100) == 0) {
                if (Instant.now().isAfter(runEndTime)) {
                    break;
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
