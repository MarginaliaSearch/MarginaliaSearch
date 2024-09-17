package nu.marginalia.sequence;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.ByteBuffer;

public interface CodedSequence  {
    byte[] bytes();

    IntIterator iterator();

    IntIterator offsetIterator(int offset);

    IntList values();

    ByteBuffer buffer();

    int bufferSize();

    int valueCount();

}
