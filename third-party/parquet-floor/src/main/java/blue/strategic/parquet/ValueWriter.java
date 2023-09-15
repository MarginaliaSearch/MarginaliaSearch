package blue.strategic.parquet;

import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;

import java.util.List;

public interface ValueWriter {
    void write(String name, Object value);
    void writeList(String name, List<?> value);
    void writeList(String name, TLongList value);
    void writeList(String name, TIntList value);
}
