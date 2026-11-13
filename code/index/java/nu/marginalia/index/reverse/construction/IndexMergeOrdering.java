package nu.marginalia.index.reverse.construction;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BinaryOperator;
import java.util.function.ToLongFunction;

/** Merges the smallest inputs first to limit repeated copying of large inputs.
 * Merged sizes are estimated by summing their inputs, allowing the merge tree
 * to be scheduled before the individual merges finish.
 */
public final class IndexMergeOrdering {
    private IndexMergeOrdering() {}

    private record SizedFuture<T>(CompletableFuture<T> future, long size)
    implements Comparable<SizedFuture<T>>
    {
        @Override
        public int compareTo(@NotNull IndexMergeOrdering.SizedFuture<T> o) {
            return Long.compare(size, o.size);
        }
    }

    public static <T> Optional<T> mergeAll(List<T> items, BinaryOperator<T> merge)
    {
        return mergeAll(ForkJoinPool.commonPool(), items, item -> 1L, merge);
    }

    public static <T> Optional<T> mergeAll(Executor executor,
                                           List<T> items,
                                           ToLongFunction<T> size,
                                           BinaryOperator<T> merge)
    {
        if (items.isEmpty()) return Optional.empty();
        if (items.size() == 1) return Optional.of(items.getFirst());

        PriorityQueue<SizedFuture<T>> queue =
                new PriorityQueue<>(items.size());

        for (T item : items) {
            queue.add(new SizedFuture<>(CompletableFuture.completedFuture(item), size.applyAsLong(item)));
        }

        while (queue.size() > 1) {
            SizedFuture<T> a = queue.poll();
            SizedFuture<T> b = queue.poll();

            long mergedSize = Math.addExact(a.size, b.size);
            CompletableFuture<T> merged = a.future.thenCombineAsync(b.future, merge, executor);
            queue.add(new SizedFuture<>(merged, mergedSize));
        }

        try {
            return Optional.of(queue.poll().future.get());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof Error err) throw err;
            throw new RuntimeException(cause);
        }
    }
}
