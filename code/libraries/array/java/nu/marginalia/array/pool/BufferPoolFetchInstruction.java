package nu.marginalia.array.pool;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

record BufferPoolFetchInstruction(PoolInstructionPriority priority,
                                  BufferEvictionPolicy evictionPolicy,
                                  long age,
                                  long address)
        implements Comparable<BufferPoolFetchInstruction> {

    public BufferPoolFetchInstruction(PoolInstructionPriority priority, BufferEvictionPolicy evictionPolicy, long address) {
        Objects.requireNonNull(priority);
        Objects.requireNonNull(evictionPolicy);

        this(priority, evictionPolicy, System.nanoTime(), address);
    }

    @Override
    public int compareTo(@NotNull BufferPoolFetchInstruction o) {
        int diff = Integer.compare(priority.ordinal(), o.priority.ordinal());
        if (diff != 0)
            return diff;

        return Long.compare(age, o.age);
    }
}
