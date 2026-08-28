package nu.marginalia.index.reverse.query;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQuery;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryInt;
import nu.marginalia.api.searchquery.model.compiled.aggregate.CompiledQueryAggregates;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

public class QueryBenchmarks {
    @State(Scope.Benchmark)
    public static class SearchState {
        CompiledQueryInt flatQueryInt;
        CompiledQueryInt complexQueryInt;

        public SearchState() {
            String[] terms = { "1", "2", "3", "4", "5", "6", "7", "8" };

            CompiledQuery<String> flatQueryStr = CompiledQuery.just(terms);

            // ( 1 2 4 | 5 ) ( 2 3 | 3 4 1 ) ( 1 | 5 6 7 8 ) ( 4 4 ( 1 1 | 2 ) )
            List<IntList> complexPaths = product(List.of(
                    List.of(IntList.of(0, 1, 3), IntList.of(4)),
                    List.of(IntList.of(1, 2), IntList.of(2, 3, 0)),
                    List.of(IntList.of(0), IntList.of(4, 5, 6, 7)),
                    List.of(IntList.of(3, 0), IntList.of(3, 1))
            ));
            CompiledQuery<String> complexQueryStr = new CompiledQuery<>(complexPaths, new int[terms.length], terms);

            System.out.println(flatQueryStr);
            System.out.println(complexQueryStr);

            flatQueryInt = flatQueryStr.mapToInt(Integer::parseInt);
            complexQueryInt = complexQueryStr.mapToInt(Integer::parseInt);
        }

        /** Expand into a full list of paths */
        private static List<IntList> product(List<List<IntList>> groups) {
            List<IntList> ret = List.of(IntList.of());

            for (List<IntList> alternatives : groups) {
                List<IntList> next = new ArrayList<>();
                for (IntList prefix : ret) {
                    for (IntList alternative : alternatives) {
                        IntList combined = new IntArrayList(prefix);
                        combined.addAll(alternative);
                        next.add(combined);
                    }
                }
                ret = next;
            }

            return ret;
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
