package nu.marginalia.index.reverse.construction;

import java.util.Comparator;
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

    private record SizedFuture<T>(CompletableFuture<T> future, long estimatedSize) {}

    public interface Mergable {
        long estimateSize();
    }

    public static <T extends Mergable> Optional<T> mergeAll(List<T> items, BinaryOperator<T> merge)
    {
        if (items.isEmpty()) return Optional.empty();
        if (items.size() == 1) return Optional.of(items.getFirst());

        PriorityQueue<SizedFuture<T>> queue =
                new PriorityQueue<>(items.size(), Comparator.comparing(SizedFuture::estimatedSize));

        for (T item : items) {
            queue.add(new SizedFuture<>(
                    CompletableFuture.completedFuture(item),
                    item.estimateSize()));
        }

        while (queue.size() > 1) {
            SizedFuture<T> a = queue.poll();
            SizedFuture<T> b = queue.poll();
            CompletableFuture<T> merged = a.future.thenCombineAsync(b.future, merge);
            queue.add(new SizedFuture<>(merged, a.estimatedSize + b.estimatedSize));
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
