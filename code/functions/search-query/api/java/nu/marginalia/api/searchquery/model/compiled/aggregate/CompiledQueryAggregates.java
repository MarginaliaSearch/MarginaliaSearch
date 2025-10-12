package nu.marginalia.api.searchquery.model.compiled.aggregate;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import nu.marginalia.api.searchquery.model.compiled.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/** Contains methods for aggregating across a CompiledQuery or CompiledQueryLong */
public class CompiledQueryAggregates {
    /** Compiled query aggregate that for a single boolean that treats or-branches as logical OR,
     * and and-branches as logical AND operations.  Will return true if there exists a path through
     * the query where the provided predicate returns true for each item.
     */
    static public <T> boolean booleanAggregate(CompiledQuery<T> query, Predicate<T> predicate) {
        final CqData<T> data = query.data;

        outer:
        for (IntList path: query.paths) {

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);
                final T dataVal = data.get(dataIdx);

                if (!predicate.test(dataVal)) {
                    continue outer;
                }
            }

            return true;
        }

        return false;
    }

    static public boolean booleanAggregate(CompiledQueryLong query, LongPredicate predicate) {
        final CqDataLong data = query.data;

        outer:
        for (IntList path: query.paths) {

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);
                final long dataVal = data.get(dataIdx);

                if (!predicate.test(dataVal)) {
                    continue outer;
                }
            }

            return true;
        }

        return false;
    }


    /** Compiled query aggregate that for a 64b bitmask that treats or-branches as logical OR,
     * and and-branches as logical AND operations.
     */
    public static <T> long longBitmaskAggregate(CompiledQuery<T> query, ToLongFunction<T> operator) {
        final CqData<T> data = query.data;
        long orResult = 0;

        for (IntList path: query.paths) {
            int andResult = ~0;

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);
                final T dataVal = data.get(dataIdx);

                final long calculationResult = operator.applyAsLong(dataVal);

                andResult &= calculationResult;
            }

            orResult |= andResult;
        }

        return orResult;
    }

    public static long longBitmaskAggregate(CompiledQueryLong query, LongUnaryOperator operator) {
        final CqDataLong data = query.data;
        long orResult = 0;

        for (IntList path: query.paths) {
            int andResult = ~0;

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);
                final long dataVal = data.get(dataIdx);

                final long calculationResult = operator.applyAsLong(dataVal);

                andResult &= calculationResult;
            }

            orResult |= andResult;
        }

        return orResult;
    }

    /** Apply the operator to each leaf node, then return the highest minimum value found along any path */
    public static <T> int intMaxMinAggregate(CompiledQuery<T> query, ToIntFunction<T> operator) {
        final CqData<T> data = query.data;
        int bestPath = Integer.MIN_VALUE;

        for (IntList path: query.paths) {
            int minForPath = Integer.MAX_VALUE;

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);
                final T dataVal = data.get(dataIdx);

                final int calculationResult = operator.applyAsInt(dataVal);

                minForPath = Math.min(minForPath, calculationResult);
            }

            bestPath = Math.max(bestPath, minForPath);
        }

        return bestPath;
    }

    /** Apply the operator to each leaf node, then return the highest minimum value found along any path */
    public static int intMaxMinAggregate(CompiledQueryInt query, IntUnaryOperator operator) {
        final CqDataInt data = query.data;
        int bestPath = Integer.MIN_VALUE;

        for (IntList path: query.paths) {
            int minForPath = Integer.MAX_VALUE;

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);
                final int dataVal = data.get(dataIdx);

                final int calculationResult = operator.applyAsInt(dataVal);

                minForPath = Math.min(minForPath, calculationResult);
            }

            bestPath = Math.max(bestPath, minForPath);
        }

        return bestPath;
    }

    /** Apply the operator to each leaf node, then return the highest sum of values found along any path.
     * Math-heads call this a tropical semiring for some reason.
     *
     * This function is applied directly to the node indexes, not to the data associated with the graph.
     * */
    public static double intMaxSumAggregateOfIndexes(CompiledQueryTopology query, IntToDoubleFunction operator) {
        double bestPath = Double.MIN_VALUE;

        for (IntList path: query.paths) {
            double sumForPath = Integer.MAX_VALUE;

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);

                final double calculationResult = operator.applyAsDouble(dataIdx);

                sumForPath += calculationResult;
            }

            bestPath = Math.max(bestPath, sumForPath);
        }

        return bestPath;
    }

    /** Apply the operator to each leaf node, then return the highest minimum value found along any path */
    public static int intMaxMinAggregate(CompiledQueryLong query, LongToIntFunction operator) {
        final CqDataLong data = query.data;
        int bestPath = Integer.MIN_VALUE;

        for (IntList path: query.paths) {
            int minForPath = Integer.MAX_VALUE;

            for (int i = 0; i < path.size(); i++) {

                final int dataIdx = path.getInt(i);
                final long dataVal = data.get(dataIdx);

                final int calculationResult = operator.applyAsInt(dataVal);

                minForPath = Math.min(minForPath, calculationResult);
            }

            bestPath = Math.max(bestPath, minForPath);
        }

        return bestPath;
    }

    /** Enumerate all possible paths through the compiled query */
    public static List<LongSet> queriesAggregate(CompiledQueryLong query) {
        final CqDataLong data = query.data;

        List<LongSet> ret = new ArrayList<>();

        for (IntList path : query.paths) {
            LongSet set = new LongArraySet(path.size());

            for (int i = 0; i < path.size(); i++) {
                final int dataIdx = path.getInt(i);
                final long dataVal = data.get(dataIdx);

                set.add(dataVal);
            }

            ret.add(set);
        }

        return ret;
    }

}
