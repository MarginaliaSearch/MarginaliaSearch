package nu.marginalia.array.trace;

public class NullTrace implements ArrayTrace {

    @Override
    public void touch(long address) {}

    @Override
    public void touch(long start, long end) {}

}
