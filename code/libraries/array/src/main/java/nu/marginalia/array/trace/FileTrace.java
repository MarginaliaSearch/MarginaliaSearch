package nu.marginalia.array.trace;

import nu.marginalia.array.LongArray;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileTrace {
    PrintStream traceWriter;
    static volatile boolean doTrace = false;

    public FileTrace(Path file) {
        try {
            traceWriter = new PrintStream(Files.newOutputStream(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public FileTrace() {
        this(Path.of("/tmp/array-trace.log"));
    }

    public static void setTrace(boolean val) {
        doTrace = val;
    }

    public void trace(int source, long start, long end) {
        if (doTrace) {
            traceWriter.printf("%d %d %d %d\n", System.nanoTime(), source, start, end);
        }
    }

    public ArrayTrace forArray(LongArray array) {
        return new ArrayTrace() {
            final int code = array.hashCode();

            @Override
            public void touch(long address) {
                trace(code, address, address+1);
            }

            @Override
            public void touch(long start, long end) {
                trace(code, start, end);
            }
        };
    }
}
