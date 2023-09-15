package blue.strategic.parquet;

/**
 * Creates and hydrates a rich domain object from a Parquet row.
 */
public interface Hydrator<U, S> {

    /**
     * Creates a new mutable instance to be hydrated.
     * @return new instance to be hydrated
     */
    U start();

    /**
     * Hydrates the target instance by applying the specified value from the Parquet row.
     * @param target object being hydrated
     * @param heading the name of the column whose value is being applied
     * @param value the value to apply
     * @return the new target
     */
    U add(U target, String heading, Object value);

    /**
     * Seals the mutable hydration target.
     * @param target object being hydrated
     * @return the sealed object
     */
    S finish(U target);
}
