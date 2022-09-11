package nu.marginalia.wmsa.edge.index.reader.query.types;

import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepFromPredicate;
import nu.marginalia.wmsa.edge.index.svc.query.types.filter.QueryFilterStepIf;
import org.junit.jupiter.api.Test;

import java.util.List;

class QueryFilterStepIfTest {
    QueryFilterStepIf even = new QueryFilterStepFromPredicate(l -> (l%2) == 0);
    QueryFilterStepIf divisibleByThree = new QueryFilterStepFromPredicate(l -> (l%3) == 0);
    QueryFilterStepIf either = QueryFilterStepIf.anyOf(List.of(even, divisibleByThree));
    @Test
    public void test() {
        long[] values = new long[100];

        for (int i = 0; i < values.length; i++) {
            values[i] = i;
        }

        int end = either.retainDestructive(values,  100);
//        end = even.retainReorder(values, end, 100);

        for (int i = 0; i < values.length; i++) {
            if (i == end) System.out.println("*");
            System.out.println(values[i]);
        }
    }
}