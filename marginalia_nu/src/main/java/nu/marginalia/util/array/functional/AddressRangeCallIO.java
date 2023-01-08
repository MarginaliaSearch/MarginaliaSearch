package nu.marginalia.util.array.functional;

import java.io.IOException;

public interface AddressRangeCallIO<T> {
    void apply(T array, int start, int end) throws IOException;
}
