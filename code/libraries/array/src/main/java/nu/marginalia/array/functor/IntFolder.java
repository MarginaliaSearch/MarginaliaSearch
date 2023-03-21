package nu.marginalia.array.functor;

import nu.marginalia.array.functional.AddressRangeCall;
import nu.marginalia.array.functional.IntBinaryOperation;
import nu.marginalia.array.page.IntArrayPage;

import java.io.IOException;

public class IntFolder implements AddressRangeCall<IntArrayPage> {
    public int acc;
    private final IntBinaryOperation operator;

    public IntFolder(int zero, IntBinaryOperation operator) {
        this.acc = zero;
        this.operator = operator;
    }

    public void apply(IntArrayPage array, int start, int end) {
        acc = array.fold(acc, start, end, operator);
    }
}
