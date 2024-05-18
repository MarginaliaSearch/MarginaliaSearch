package nu.marginalia.array.algo;

import java.io.IOException;

public interface LongArrayTransformations extends LongArrayBase {

    /** Applies the provided consumer to each element in the array range */
    default void forEach(long start, long end, LongLongConsumer consumer) {
        for (long i = start; i < end; i++) {
            consumer.accept(i, get(i));
        }
    }

    /** Transforms each element in the array range using the provided transformer,
     * so that array[i] = transformer.apply(i, array[i]) */
    default void transformEach(long start, long end, LongTransformer transformer) {
        for (long i = start; i < end; i++) {
            set(i, transformer.transform(i, get(i)));
        }
    }

    /** Transforms each element in the array range using the provided transformer,
     * so that array[i] = transformer.apply(i, array[i]) */
    default void transformEachIO(long start, long end, LongIOTransformer transformer) throws IOException {
        for (long i = start; i < end; i++) {
            set(i, transformer.transform(i, get(i)));
        }
    }

    /** Transforms each element in the array range using the provided transformer,
     * so that array[i] = transformer.apply(i, operator.apply(i-1, ...)) */
    default long foldIO(long zero, long start, long end, LongBinaryIOOperation operator) throws IOException {
        long accumulator = zero;

        for (long i = start; i < end; i++) {
            accumulator = operator.apply(accumulator, get(i));
        }

        return accumulator;
    }

    /** Transforms each element in the array range using the provided transformer,
     * so that array[i] = transformer.apply(i, operator.apply(i-1, ...)) */
    default long fold(long zero, long start, long end, LongBinaryOperation operator) {
        long accumulator = zero;

        for (long i = start; i < end; i++) {
            accumulator = operator.apply(accumulator, get(i));
        }

        return accumulator;
    }

    interface LongBinaryIOOperation {
        long apply(long left, long right) throws IOException;
    }

    interface LongBinaryOperation {
        long apply(long left, long right);
    }

    interface LongIOTransformer {
        long transform(long pos, long old) throws IOException;
    }

    interface LongLongConsumer {
        void accept(long pos, long val);
    }

    interface LongTransformer {
        long transform(long pos, long old);
    }
}
