package nu.marginalia.piping;

public interface PipeDrain<T> {
    boolean accept(T value);
}
