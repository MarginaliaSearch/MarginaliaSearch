package nu.marginalia.loading;

import com.github.luben.zstd.RecyclingBufferPool;
import com.github.luben.zstd.ZstdInputStream;
import lombok.SneakyThrows;
import nu.marginalia.converting.instruction.Instruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.ref.Cleaner;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConvertedDomainReader {
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private static final Logger logger = LoggerFactory.getLogger(ConvertedDomainReader.class);

    /** Creates a new iterator over Path.  The implementation will try to read the file in a separate thread, and
     * will block until the first instruction is available. Iterator$hasNext may block.
     */
    public Iterator<Instruction> createIterator(Path path) {
        return new PrefetchingInstructionIterator(path);
    }

    class PrefetchingInstructionIterator implements  Iterator<Instruction> {

        private final LinkedBlockingQueue<Instruction> queue = new LinkedBlockingQueue<>(16);
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private Instruction next = null;

        private final static Cleaner cleaner = Cleaner.create();
        static class CancelAction implements Runnable {
            private final Future<?> future;

            public CancelAction(Future<Object> taskFuture) {
                this.future = taskFuture;
            }

            public void run() {
                future.cancel(true);
            }

        }

        public PrefetchingInstructionIterator(Path path) {
            var taskFuture = executorService.submit(() -> readerThread(path));

            // Cancel the future if the iterator is garbage collected
            // to reduce the risk of leaking resources; as the worker thread
            // will spin forever on put if the queue is full.

            cleaner.register(this, new CancelAction(taskFuture));
        }

        private Object readerThread(Path path) {
            try (var or = new ObjectInputStream(new ZstdInputStream(new BufferedInputStream(new FileInputStream(path.toFile())), RecyclingBufferPool.INSTANCE))) {
                for (;;) {
                    var nextObject = or.readObject();
                    if (nextObject instanceof Instruction is) {
                        queue.put(is);
                    } else {
                        logger.warn("Spurious object in file: {}", nextObject.getClass().getSimpleName());
                    }
                }
            } catch (EOFException ex) {
                // Expected
                return null;
            } catch (ClassNotFoundException | IOException | InterruptedException e) {
                logger.warn("Error reading file " + path, e);
                throw new RuntimeException(e);
            } finally {
                finished.set(true);
            }
        }

        @SneakyThrows
        @Override
        public boolean hasNext() {
            if (next != null)
                return true;

            // As long as the worker is still running, we'll do a blocking poll to wait for the next instruction
            // (but we wake up every second to check if the worker is still running)
            while (!finished.get()) {
                if (null != (next = queue.poll(1, TimeUnit.SECONDS))) {
                    return true;
                }
            }

            // If the worker is not running, we just drain the queue without waiting
            return null != (next = queue.poll());
        }

        @Override
        public Instruction next() {
            if (next != null || hasNext()) {
                try { return next; }
                finally { next = null; }
            }
            throw new IllegalStateException();
        }

    }

}
