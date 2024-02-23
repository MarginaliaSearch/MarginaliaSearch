package nu.marginalia.process.log;

import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Function;

class WorkLoadIterable<T> implements Iterable<T> {

    private final Path logFile;
    private final Function<WorkLogEntry, Optional<T>> mapper;

    WorkLoadIterable(Path logFile, Function<WorkLogEntry, Optional<T>> mapper) {
        this.logFile = logFile;
        this.mapper = mapper;
    }

    @NotNull
    @Override
    @SneakyThrows
    public Iterator<T> iterator() {
        var stream = Files.lines(logFile);
        return new Iterator<>() {
            final Iterator<T> iter = stream
                    .filter(WorkLogEntry::isJobId)
                    .map(WorkLogEntry::parse)
                    .map(mapper)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .iterator();

            @Override
            public boolean hasNext() {
                if (iter.hasNext()) {
                    return true;
                } else {
                    stream.close();
                    return false;
                }
            }

            @Override
            public T next() {
                return iter.next();
            }
        };
    }
}
