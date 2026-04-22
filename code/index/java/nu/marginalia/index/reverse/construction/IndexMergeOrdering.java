package nu.marginalia.index.reverse.construction;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BinaryOperator;

/** Greedy merge of items using a size-sorted priority queue.
 * This should find an <a href="https://xlinux.nist.gov/dads/HTML/optimalMerge.html">optimal merge</a> pattern.
 *
 */
public final class IndexMergeOrdering {
    private IndexMergeOrdering() {}

    private record SizedFuture<T>(CompletableFuture<T> future, int size)
    implements Comparable<SizedFuture<T>>
    {
        public SizedFuture(T item) {
            this(CompletableFuture.completedFuture(item), 1);
        }

        @Override
        public int compareTo(@NotNull IndexMergeOrdering.SizedFuture<T> o) {
            return Integer.compare(size, o.size);
        }
    }

    public static <T> Optional<T> mergeAll(List<T> items, BinaryOperator<T> merge)
    {
        if (items.isEmpty()) return Optional.empty();
        if (items.size() == 1) return Optional.of(items.getFirst());

        PriorityQueue<SizedFuture<T>> queue =
                new PriorityQueue<>(items.size());

        for (T item : items) {
            queue.add(new SizedFuture<>(item));
        }

        while (queue.size() > 1) {
            SizedFuture<T> a = queue.poll();
            SizedFuture<T> b = queue.poll();

            CompletableFuture<T> merged = a.future.thenCombineAsync(b.future, merge);
            queue.add(new SizedFuture<>(merged, a.size + b.size));
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
