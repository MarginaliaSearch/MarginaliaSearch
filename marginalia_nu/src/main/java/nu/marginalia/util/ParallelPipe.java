package nu.marginalia.util;

import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public abstract class ParallelPipe<INPUT,INTERMEDIATE> {
    private final LinkedBlockingQueue<INPUT> inputs;
    private final LinkedBlockingQueue<INTERMEDIATE> intermediates;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final List<Thread> processThreads = new ArrayList<>();
    private final Thread receiverThread;

    private volatile boolean expectingInput = true;
    private volatile boolean expectingOutput = true;

    public ParallelPipe(String name, int numberOfThreads, int inputQueueSize, int intermediateQueueSize) {
        inputs = new LinkedBlockingQueue<>(inputQueueSize);
        intermediates = new LinkedBlockingQueue<>(intermediateQueueSize);

        for (int i = 0; i < numberOfThreads; i++) {
            processThreads.add(new Thread(this::runProcessThread, name + "-process["+i+"]"));
        }
        receiverThread = new Thread(this::runReceiverThread, name + "-receiver");

        processThreads.forEach(Thread::start);
        receiverThread.start();
    }

    public void clearQueues() {
        inputs.clear();
        intermediates.clear();
    }

    @SneakyThrows
    private void runProcessThread() {
        while (expectingInput || !inputs.isEmpty()) {
            var in = inputs.poll(1, TimeUnit.SECONDS);

            if (in != null) {
                try {
                    var ret = onProcess(in);
                    if (ret != null) {
                        intermediates.put(ret);
                    }
                }
                catch (InterruptedException ex) {
                    throw ex;
                }
                catch (Exception ex) {
                    logger.error("Exception", ex);
                }

            }
        }

        logger.debug("Terminating {}", Thread.currentThread().getName());
    }
    @SneakyThrows
    private void runReceiverThread() {
        while (expectingOutput || !inputs.isEmpty() || !intermediates.isEmpty()) {
            var intermediate = intermediates.poll(997, TimeUnit.MILLISECONDS);
            if (intermediate != null) {
                try {
                    onReceive(intermediate);
                }
                catch (Exception ex) {
                    logger.error("Exception", ex);
                }
            }
        }

        logger.info("Terminating {}", Thread.currentThread().getName());
    }

    @SneakyThrows
    public void accept(INPUT input) {
        inputs.put(input);
    }

    protected abstract INTERMEDIATE onProcess(INPUT input) throws Exception;
    protected abstract void onReceive(INTERMEDIATE intermediate) throws Exception;

    public void join() throws InterruptedException {
        expectingInput = false;

        for (var thread : processThreads) {
            thread.join();
        }

        expectingOutput = false;
        receiverThread.join();
    }
}
