package nu.marginalia.util.array.functor;

import nu.marginalia.util.array.functional.AddressRangeCallIO;
import nu.marginalia.util.array.functional.LongBinaryIOOperation;
import nu.marginalia.util.array.page.LongArrayPage;

import java.io.IOException;

public class LongIOFolder implements AddressRangeCallIO<LongArrayPage> {
    public long acc;
    private final LongBinaryIOOperation operator;

    public LongIOFolder(long zero, LongBinaryIOOperation operator) {
        this.acc = zero;
        this.operator = operator;
    }

    public void apply(LongArrayPage array, int start, int end) throws IOException {
        acc = array.foldIO(acc, start, end, operator);
    }
}
