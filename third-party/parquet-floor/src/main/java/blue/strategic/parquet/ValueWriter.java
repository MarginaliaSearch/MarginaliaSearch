package blue.strategic.parquet;

import java.util.List;

public interface ValueWriter {
    void write(String name, Object value);
    void writeList(String name, List<?> value);
}
