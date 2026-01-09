package nu.marginalia.piping;

public interface StageExecution<T> {
    void accept(T val);
    void cleanUp();
}
