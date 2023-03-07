package nu.marginalia.array.algo;

import nu.marginalia.array.functional.LongBinaryIOOperation;
import nu.marginalia.array.functional.LongIOTransformer;
import nu.marginalia.array.functional.LongLongConsumer;
import nu.marginalia.array.functional.LongTransformer;

import java.io.IOException;

public interface LongArrayTransformations extends LongArrayBase {

    default void forEach(long start, long end, LongLongConsumer consumer) {
        for (long i = start; i < end; i++) {
            consumer.accept(i, get(i));
        }
    }

    default void transformEach(long start, long end, LongTransformer transformer) {
        for (long i = start; i < end; i++) {
            set(i, transformer.transform(i, get(i)));
        }
    }

    default void transformEachIO(long start, long end, LongIOTransformer transformer) throws IOException {
        for (long i = start; i < end; i++) {
            set(i, transformer.transform(i, get(i)));
        }
    }

    default long foldIO(long zero, long start, long end, LongBinaryIOOperation operator) throws IOException {
        long accumulator = zero;

        for (long i = start; i < end; i++) {
            accumulator = operator.apply(accumulator, get(i));
        }

        return accumulator;
    }

}
