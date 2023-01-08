package nu.marginalia.util.array.trace;

public class NullTrace implements ArrayTrace {

    @Override
    public void touch(long address) {}

    @Override
    public void touch(long start, long end) {}

}
