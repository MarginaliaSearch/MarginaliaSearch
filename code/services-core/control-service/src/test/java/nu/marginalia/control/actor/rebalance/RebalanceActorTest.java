package nu.marginalia.control.actor.rebalance;

import nu.marginalia.actor.prototype.AbstractActorPrototype;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

class RebalanceActorTest {
    RebalanceActor actor = new RebalanceActor(null, null, null, null);

    @Test
    void calculateTransactions1_2() {
        var transactions = actor.calculateTransactions(Map.of(1, 100, 2, 0));
        var expected = List.of(new RebalanceActor.Give(1, 2, 50));

        Assertions.assertEquals(expected, transactions);
    }

    @Test
    void calculateTransactions1_3() {
        var transactions = actor.calculateTransactions(Map.of(1, 90, 2, 0, 3, 0));
        var expected = List.of(
                new RebalanceActor.Give(1, 2, 30),
                new RebalanceActor.Give(1, 3, 30)
        );

        Assertions.assertEquals(expected, transactions);
    }

    @Test
    void calculateTransactions2_3() {
        var transactions = actor.calculateTransactions(Map.of(1, 30, 2, 30, 3, 0));
        var expected = List.of(
                new RebalanceActor.Give(1, 3, 10),
                new RebalanceActor.Give(2, 3, 10)
        );

        Assertions.assertEquals(expected, transactions);
    }

    @Test
    void calculateTransactionsEmpty() {
        try {
            actor.calculateTransactions(Map.of());
            Assertions.fail("Expected transition");
        }
        catch (AbstractActorPrototype.ControlFlowException ex) {
            Assertions.assertEquals("END", ex.getState());
        }

        try {
            actor.calculateTransactions(Map.of(1, 100));
            Assertions.fail("Expected transition");
        }
        catch (AbstractActorPrototype.ControlFlowException ex) {
            Assertions.assertEquals("END", ex.getState());
        }
    }
}