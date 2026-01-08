package nu.marginalia.asyncio;

import java.lang.foreign.MemorySegment;

public record AsyncReadRequest(int fd, MemorySegment destination, long offset) {
}