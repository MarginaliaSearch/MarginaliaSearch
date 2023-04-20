package nu.marginalia.array;

/** A reference to a range of an array. Use this class judiciously to avoid
 * gc churn.
 */
public record ArrayRangeReference<T>(T array, long start, long end) {
}
