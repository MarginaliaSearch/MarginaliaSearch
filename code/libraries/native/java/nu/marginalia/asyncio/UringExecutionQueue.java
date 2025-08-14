package nu.marginalia.asyncio;

import nu.marginalia.ffi.IoUring;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.foreign.ValueLayout.*;

public class UringExecutionQueue implements AutoCloseable {
    private static final IoUring ioUringInstance = IoUring.instance();

    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final int queueSize;

    private final Thread executor;
    private volatile boolean running = true;
    private final MemorySegment uringQueue;

    private final ArrayBlockingQueue<SubmittedReadRequest<? extends Object>> inputQueue;

    public UringExecutionQueue(int queueSize) throws Throwable {
        this.inputQueue = new ArrayBlockingQueue<>(queueSize, false);
        this.queueSize = queueSize;
        this.uringQueue = (MemorySegment) ioUringInstance.uringInit.invoke(queueSize);

        executor = Thread.ofPlatform().daemon().start(this::executionPipe);
    }

    public void close() throws InterruptedException {
        running = false;
        executor.join();

        try {
            ioUringInstance.uringClose.invoke(uringQueue);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public <T> CompletableFuture<T> submit(T context, List<AsyncReadRequest> relatedRequests) throws InterruptedException {
        if (relatedRequests.size() > queueSize) {
            throw new IllegalArgumentException("Request batches may not exceed the queue size!");
        }
        long id = requestIdCounter.incrementAndGet();
        CompletableFuture<T> future = new CompletableFuture<>();
        inputQueue.put(new SubmittedReadRequest<>(context, relatedRequests, future, id));

        return future;
    }

    static class UringDispatcher implements AutoCloseable {
        private final Arena arena;

        private final MemorySegment returnResultIds;
        private final MemorySegment readBatchIds;
        private final MemorySegment readFds;
        private final MemorySegment readBuffers;
        private final MemorySegment readSizes;
        private final MemorySegment readOffsets;
        private final MemorySegment uringQueue;

        private int requestsToSend = 0;

        UringDispatcher(int queueSize, MemorySegment uringQueue) {
            this.uringQueue = uringQueue;
            this.arena = Arena.ofConfined();

            returnResultIds = arena.allocate(JAVA_LONG, queueSize);
            readBatchIds = arena.allocate(JAVA_LONG, queueSize);
            readFds = arena.allocate(JAVA_INT, queueSize);
            readBuffers = arena.allocate(ADDRESS, queueSize);
            readSizes = arena.allocate(JAVA_INT, queueSize);
            readOffsets = arena.allocate(JAVA_LONG, queueSize);
        }

        void prepareRead(int fd, long batchId, MemorySegment segment, int size, long offset) {
            readFds.setAtIndex(JAVA_INT, requestsToSend, fd);
            readBuffers.setAtIndex(ADDRESS, requestsToSend, segment);
            readBatchIds.setAtIndex(JAVA_LONG, requestsToSend, batchId);
            readSizes.setAtIndex(JAVA_INT, requestsToSend, size);
            readOffsets.setAtIndex(JAVA_LONG, requestsToSend, offset);
            requestsToSend++;
        }

        long[] poll() {
            try {
                // Dispatch call
                int result = (Integer) IoUring.instance.uringJustPoll.invoke(uringQueue, returnResultIds);

                if (result < 0) {
                    throw new IOException("Error in io_uring");
                }
                else {
                    long[] ret = new long[result];
                    for (int i = 0; i < result; i++) {
                        ret[i] = returnResultIds.getAtIndex(JAVA_LONG, i);
                    }
                    return ret;
                }

            }
            catch (Throwable e) {
                throw new RuntimeException(e);
            }
            finally {
                requestsToSend = 0;
            }
        }
        long[] dispatchRead(int ongoingRequests) throws IOException {
            try {
                // Dispatch call
                int result = (Integer) IoUring.instance.uringReadAndPoll.invoke(
                        uringQueue,
                        returnResultIds,
                        ongoingRequests,
                        requestsToSend,
                        readBatchIds,
                        readFds,
                        readBuffers,
                        readSizes,
                        readOffsets
                );

                if (result < 0) {
                    throw new IOException("Error in io_uring");
                }
                else {
                    long[] ret = new long[result];
                    for (int i = 0; i < result; i++) {
                        ret[i] = returnResultIds.getAtIndex(JAVA_LONG, i);
                    }
                    return ret;
                }

            }
            catch (Throwable e) {
                throw new RuntimeException(e);
            }
            finally {
                requestsToSend = 0;
            }
        }

        int getRequestsToSend() {
            return requestsToSend;
        }

        public void close() {
            arena.close();
        }
    }

    public void executionPipe() {
        try (var uringDispatcher = new UringDispatcher(queueSize, uringQueue)) {
            int ongoingRequests = 0;

            // recycle between iterations to avoid allocation churn
            List<SubmittedReadRequest<?>> batchesToSend = new ArrayList<>();

            Map<Long, SubmittedReadRequest<?>> requestsToId = new HashMap<>();

            while (running) {
                batchesToSend.clear();

//                if (inputQueue.isEmpty() && ongoingRequests == 0) {
//                    LockSupport.parkNanos(10_000);
//                    continue;
//                }

                int remainingRequests = queueSize - ongoingRequests;

                SubmittedReadRequest<?> request;

                // Find batches to send that will not exceed the queue size
                while ((request = inputQueue.peek()) != null) {
                    if (remainingRequests >= request.count()) {
                        remainingRequests -= request.count();
                        inputQueue.poll();

                        batchesToSend.add(request);
                    }
                    else {
                        break;
                    }
                }

                // Arrange requests from the batches into arrays to send to FFI call

                int requestsToSend = 0;
                for (var batch : batchesToSend) {
                    requestsToId.put(batch.id, batch);

                    for (var read : batch.getRequests()) {
                        uringDispatcher.prepareRead(read.fd(), batch.id, read.destination(), (int) read.destination().byteSize(), read.offset());
                    }
                }

                try {
                    ongoingRequests += uringDispatcher.getRequestsToSend();

                    long[] results;
                    if (uringDispatcher.getRequestsToSend() > 0) {
                        results = uringDispatcher.dispatchRead(ongoingRequests);
                    }
                    else {
                        results = uringDispatcher.poll();
                    }

                    for (long id : results) {
                        requestsToId.computeIfPresent(Math.abs(id), (_, req) -> {
                            if (req.partFinished(id > 0)) {
                                return null;
                            } else {
                                return req;
                            }
                        });
                        ongoingRequests--;
                    }
                }
                catch (IOException ex) {
                    ongoingRequests -= requestsToSend;
                    batchesToSend.forEach(req -> {
                        req.canNotFinish();
                        requestsToId.remove(req.id);
                    });
                }
                catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

}
