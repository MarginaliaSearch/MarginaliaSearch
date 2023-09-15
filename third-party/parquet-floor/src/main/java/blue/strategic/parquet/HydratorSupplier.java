package blue.strategic.parquet;

import org.apache.parquet.column.ColumnDescriptor;

import java.util.List;

/**
 * Supplies hydrdators.
 */
public interface HydratorSupplier<U, S> {
    /**
     * Supplies a hydrdator from the specified list of columns. Values will always be added to the hydrator
     * in the same order as the columns supplied to this function.
     */
    Hydrator<U, S> get(List<ColumnDescriptor> columns);

    static <A, B> HydratorSupplier<A, B> constantly(final Hydrator<A, B> hydrator) {
        return columns -> hydrator;
    }
}
