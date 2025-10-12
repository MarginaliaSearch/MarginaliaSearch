package nu.marginalia.index.reverse.query;

import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryInt;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryParser;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import org.openjdk.jmh.annotations.*;

import java.util.function.IntUnaryOperator;

public class QueryBenchmarks {
    @State(Scope.Benchmark)
    public static class SearchState {
        CompiledQueryInt flatQueryInt;
        CompiledQueryInt complexQueryInt;

        public SearchState() {
            CompiledQuery<String> flatQueryStr = CompiledQueryParser.parse("1 2 3 4 5 6 7 8");
            CompiledQuery<String> complexQueryStr = CompiledQueryParser.parse("( 1 2 4 | 5 ) ( 2 3 | 3 4 1 ) ( 1 | 5 6 7 8 ) ( 4 4 ( 1 1 | 2 ) )");

            System.out.println(flatQueryStr.root.toString());
            System.out.println(complexQueryStr.root.toString());

            flatQueryInt = flatQueryStr.mapToInt(Integer::parseInt);
            complexQueryInt = complexQueryStr.mapToInt(Integer::parseInt);
        }
    }

    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 5)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long flatReduce(SearchState state) {
        return CompiledQueryAggregates.intMaxMinAggregate(state.flatQueryInt, new IntUnaryOperator() {
            @Override
            public int applyAsInt(int i) {
                return i+1;
            }
        });
    };


    @Fork(value = 1, warmups = 1)
    @Warmup(iterations = 5)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public long complexReduce(SearchState state) {
        return CompiledQueryAggregates.intMaxMinAggregate(state.complexQueryInt, new IntUnaryOperator() {
            @Override
            public int applyAsInt(int i) {
                return i+1;
            }
        });
    };

}
