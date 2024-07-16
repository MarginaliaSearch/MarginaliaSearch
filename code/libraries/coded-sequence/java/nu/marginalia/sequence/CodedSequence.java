package nu.marginalia.sequence;

import blue.strategic.parquet.BinarySerializable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;

import java.nio.ByteBuffer;

public interface CodedSequence extends BinarySerializable  {
    byte[] bytes();

    IntIterator iterator();

    IntIterator offsetIterator(int offset);

    IntList values();

    ByteBuffer buffer();

    int bufferSize();

    int valueCount();
}
