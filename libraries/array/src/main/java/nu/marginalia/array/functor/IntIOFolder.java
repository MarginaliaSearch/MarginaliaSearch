package nu.marginalia.array.functor;

import nu.marginalia.array.functional.AddressRangeCallIO;
import nu.marginalia.array.functional.IntBinaryIOOperation;
import nu.marginalia.array.page.IntArrayPage;

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
