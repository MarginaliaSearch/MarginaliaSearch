package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.ByteBuffer;

public interface CodedSequence  {
    byte[] bytes();

    IntIterator iterator();

    IntIterator offsetIterator(int offset);

    IntList values();

    IntList values(Int2ObjectFunction<IntArrayList> allocator);


    ByteBuffer buffer();

    int bufferSize();

    int valueCount();

}
