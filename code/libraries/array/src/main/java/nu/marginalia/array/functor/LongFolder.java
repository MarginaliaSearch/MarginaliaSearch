package nu.marginalia.array.functor;

import nu.marginalia.array.functional.AddressRangeCall;
import nu.marginalia.array.functional.LongBinaryOperation;
import nu.marginalia.array.page.LongArrayPage;

public class LongFolder implements AddressRangeCall<LongArrayPage> {
    public long acc;
    private final LongBinaryOperation operator;

    public LongFolder(long zero, LongBinaryOperation operator) {
        this.acc = zero;
        this.operator = operator;
    }

    public void apply(LongArrayPage array, int start, int end) {
        acc = array.fold(acc, start, end, operator);
    }
}
