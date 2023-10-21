package nu.marginalia.control.actor.rebalance;

import nu.marginalia.actor.prototype.AbstractActorPrototype;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static nu.marginalia.control.actor.rebalance.RebalanceActor.*;
import static org.junit.Assert.assertEquals;

class RebalanceActorTest {
    RebalanceActor actor = new RebalanceActor(null, null, null, null);

    @Test
    void calculateTransactions1_2() {
        var transactions = actor.calculateTransactions(
                List.of(new Pop(1, 100), new Pop(2, 0))
        );
        var expected = List.of(new Give(1, 2, 50));

        Assertions.assertEquals(expected, transactions);
    }

    @Test
    void calculateTransactions1_3() {
        var transactions = actor.calculateTransactions(
                List.of(
                        new Pop(1, 90),
                        new Pop(2, 0),
                        new Pop(3, 0)
                )
        );
        var expected = List.of(
                new Give(1, 2, 30),
                new Give(1, 3, 30)
        );

        Assertions.assertEquals(expected, transactions);
    }

    @Test
    void calculateTransactions2_3() {
        var transactions = actor.calculateTransactions(
                List.of(
                        new Pop(1, 30),
                        new Pop(2, 30),
                        new Pop(3, 0)
                )
        );
        var expected = List.of(
                new Give(1, 3, 10),
                new Give(2, 3, 10)
        );

        Assertions.assertEquals(expected, transactions);
    }

    @Test
    void calculateTransactionsEmpty() {
        try {
            actor.calculateTransactions(List.of());
            Assertions.fail("Expected transition");
        }
        catch (AbstractActorPrototype.ControlFlowException ex) {
            Assertions.assertEquals("END", ex.getState());
        }

        try {
            actor.calculateTransactions(List.of(new Pop(1, 100)));
            Assertions.fail("Expected transition");
        }
        catch (AbstractActorPrototype.ControlFlowException ex) {
            Assertions.assertEquals("END", ex.getState());
        }
    }

}