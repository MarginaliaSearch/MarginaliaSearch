package nu.marginalia.asyncio;

import nu.marginalia.ffi.IoUring;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.foreign.ValueLayout.*;

public class UringExecutionQueue implements AutoCloseable {
    private static final IoUring ioUringInstance = IoUring.instance();

    private final AtomicLong requestIdCounter = new AtomicLong(1);
    private final int queueSize;

    private final Thread executor;
    private volatile boolean running = true;
    private final MemorySegment uringQueue;

    private final RingBufferSPNC<SubmittedReadRequest<? extends Object>> inputQueue;
    private final LongRingBufferSPSC completionQueue;

    public UringExecutionQueue(int queueSize) throws IOException {
        this.inputQueue = new RingBufferSPNC<>(4);
        this.completionQueue = new LongRingBufferSPSC(queueSize);
        this.queueSize = queueSize;
        try {
            this.uringQueue = (MemorySegment) ioUringInstance.uringInitUnregistered.invoke(queueSize);
        }
        catch (Throwable ex) {
            throw new IOException("Error initializing io_uring", ex);
        }
        String id = UUID.randomUUID().toString();
        executor = Thread.ofPlatform()
                .name("UringExecutionQueue[%s]$executionPipe".formatted(id))
                .daemon().start(this::executionPipe);

        Thread.ofPlatform()
                .name("UringExecutionQueue[%s]$completionHandler".formatted(id))
                .daemon().start(this::completionHandler);
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

        if (relatedRequests.isEmpty())
            return CompletableFuture.completedFuture(context);

        long id = requestIdCounter.incrementAndGet();
        CompletableFuture<T> future = new CompletableFuture<>();

        var item = new SubmittedReadRequest<>(context, relatedRequests, future, id);
        while (!inputQueue.put(item))
            Thread.yield();

        return future;
    }

    public <T> CompletableFuture<T> submit(T context, AsyncReadRequest request) throws InterruptedException {
        long id = requestIdCounter.incrementAndGet();
        CompletableFuture<T> future = new CompletableFuture<>();

        var item = new SubmittedReadRequest<>(context, List.of(request), future, id);
        while (!inputQueue.put(item))
            Thread.yield();

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
        long[] resultBuffer = new long[512];

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

        int poll() {
            try {
                // Dispatch call
                int result = (Integer) IoUring.instance.uringJustPoll.invoke(uringQueue, returnResultIds);

                if (result < 0) {
                    throw new IOException("Error in io_uring");
                }
                else {
                    for (int i = 0; i < result; i++) {
                        resultBuffer[i] = returnResultIds.getAtIndex(JAVA_LONG, i);
                    }
                    return result;
                }

            }
            catch (Throwable e) {
                throw new RuntimeException(e);
            }
            finally {
                requestsToSend = 0;
            }
        }
        int dispatchRead(int ongoingRequests) throws IOException {
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
                    for (int i = 0; i < result; i++) {
                        resultBuffer[i] = returnResultIds.getAtIndex(JAVA_LONG, i);
                    }
                    return result;
                }

            }
            catch (Throwable e) {
                throw new RuntimeException(e);
            }
            finally {
                requestsToSend = 0;
            }
        }

        long[] getResultBuffer() {
            return resultBuffer;
        }

        int getRequestsToSend() {
            return requestsToSend;
        }

        public void close() {
            arena.close();
        }
    }

    private final Map<Long, SubmittedReadRequest<?>> requestsToId = new ConcurrentHashMap<>();

    public void completionHandler() {
        long[] completions = new long[32];
        while (running) {
            int n = completionQueue.takeNonBlock(completions);
            if (n == 0) {
                Thread.yield();
            }
            else {
                for (int i = 0; i < n; i++) {
                    long id = completions[i];
                    Long absid = Math.abs(id);
                    var req = requestsToId.get(absid);
                    if (req != null && req.partFinished(id > 0)) {
                        requestsToId.remove(absid);
                    }
                }
            }

        }
    }

    public void executionPipe() {
        try (var uringDispatcher = new UringDispatcher(queueSize, uringQueue)) {
            int ongoingRequests = 0;

            // recycle between iterations to avoid allocation churn
            List<SubmittedReadRequest<?>> batchesToSend = new ArrayList<>();

            while (running) {
                batchesToSend.clear();

                int remainingRequests = queueSize - ongoingRequests;

                SubmittedReadRequest<?> request;

                // Find batches to send that will not exceed the queue size
                while ((request = inputQueue.peek()) != null) {
                    if (remainingRequests >= request.count()) {
                        remainingRequests -= request.count();
                        inputQueue.take();

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
                        uringDispatcher.prepareRead(read.fd(),
                                batch.id,
                                read.destination(),
                                (int) read.destination().byteSize(),
                                read.offset());
                    }
                }

                try {
                    ongoingRequests += uringDispatcher.getRequestsToSend();

                    int results;
                    if (uringDispatcher.getRequestsToSend() > 0) {
                        results = uringDispatcher.dispatchRead(ongoingRequests);
                    }
                    else {
                        results = uringDispatcher.poll();
                    }

                    completionQueue.put(uringDispatcher.getResultBuffer(), results);
                    ongoingRequests-=results;
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