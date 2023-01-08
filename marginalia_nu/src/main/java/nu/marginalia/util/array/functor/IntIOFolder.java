package nu.marginalia.util.array.functor;

import nu.marginalia.util.array.functional.AddressRangeCallIO;
import nu.marginalia.util.array.functional.IntBinaryIOOperation;
import nu.marginalia.util.array.page.IntArrayPage;

import java.io.IOException;

public class IntIOFolder implements AddressRangeCallIO<IntArrayPage> {
    public int acc;
    private final IntBinaryIOOperation operator;

    public IntIOFolder(int zero, IntBinaryIOOperation operator) {
        this.acc = zero;
        this.operator = operator;
    }

    public void apply(IntArrayPage array, int start, int end) throws IOException {
        acc = array.foldIO(acc, start, end, operator);
    }
}
