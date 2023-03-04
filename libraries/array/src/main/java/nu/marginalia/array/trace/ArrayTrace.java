package nu.marginalia.array.trace;

import nu.marginalia.array.LongArray;

import java.nio.file.Path;
import java.util.Optional;

public interface ArrayTrace {
    void touch(long address);
    void touch(long start, long end);

    FileTrace fileTrace = Optional.ofNullable(System.clearProperty("nu.marginalia.util.array.trace")).map(Path::of).map(FileTrace::new).orElseGet(FileTrace::new);
    NullTrace nullTrace = new NullTrace();
    static ArrayTrace get(LongArray array) {

        if (fileTrace == null) {
            return nullTrace;
        }

        return fileTrace.forArray(array);
    }
}
