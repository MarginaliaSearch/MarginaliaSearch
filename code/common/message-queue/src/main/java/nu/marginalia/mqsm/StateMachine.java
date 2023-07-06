package nu.marginalia.mqsm;

import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.inbox.MqInbox;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSubscription;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/** A state machine that can be used to implement a finite state machine
 * using a message queue as the persistence layer.  The state machine is
 * resilient to crashes and can be resumed from the last state.
 */
public class StateMachine {
    private final Logger logger = LoggerFactory.getLogger(StateMachine.class);

    private final MqInbox smInbox;
    private final MqOutbox smOutbox;
    private final String queueName;
    private MachineState state;

    private final MachineState errorState = new StateFactory.ErrorState();
    private final MachineState finalState = new StateFactory.FinalState();
    private final MachineState resumingState = new StateFactory.ResumingState();

    private final Map<String, MachineState> allStates = new HashMap<>();

    public StateMachine(MqPersistence persistence,
                        String queueName,
                        UUID instanceUUID,
                        AbstractStateGraph stateGraph) {
        this.queueName = queueName;

        smInbox = new MqInbox(persistence, queueName, instanceUUID, Executors.newSingleThreadExecutor());
        smOutbox = new MqOutbox(persistence, queueName, instanceUUID);

        smInbox.subscribe(new StateEventSubscription());

        registerStates(List.of(errorState, finalState, resumingState));
        registerStates(stateGraph);

        for (var declaredState : stateGraph.declaredStates()) {
            if (!allStates.containsKey(declaredState)) {
                throw new IllegalArgumentException("State " + declaredState + " is not defined in the state graph");
            }
        }
    }

    /** Register the state graph */
    void registerStates(List<MachineState> states) {
        for (var state : states) {
            allStates.put(state.name(), state);
        }
    }

    /** Register the state graph */
    void registerStates(AbstractStateGraph states) {
        registerStates(states.asStateList());
    }

    /** Wait for the state machine to reach a final state.
     * (possibly forever, halting problem and so on)
     */
    public void join() throws InterruptedException {
        synchronized (this) {
            if (null == state)
                return;

            while (!state.isFinal()) {
                wait();
            }
        }
    }


    /** Initialize the state machine. */
    public void init() throws Exception {
        var transition = StateTransition.to("INITIAL");

        synchronized (this) {
            this.state = allStates.get(transition.state());
            notifyAll();
        }

        smInbox.start();
        smOutbox.notify(transition.state(), transition.message());
    }

    /** Resume the state machine from the last known state. */
    public void resume() throws Exception {

        if (state != null) {
            return;
        }

        var messages = smInbox.replay(1);
        if (messages.isEmpty()) {
            init();
            return;
        }

        var firstMessage = messages.get(0);
        var resumeState = allStates.get(firstMessage.function());

        smInbox.start();
        logger.info("Resuming state machine from {}({})/{}", firstMessage.function(), firstMessage.payload(), firstMessage.state());

        if (firstMessage.state() == MqMessageState.NEW) {
            // The message is not acknowledged, so starting the inbox will trigger a state transition
            // We still need to set a state here so that the join() method works

            state = resumingState;
        } else if (resumeState.resumeBehavior().equals(ResumeBehavior.ERROR)) {
            // The message is acknowledged, but the state does not support resuming
            smOutbox.notify("ERROR", "Illegal resumption from ACK'ed state " + firstMessage.function());
        } else {
            // The message is already acknowledged, so we replay the last state
            onStateTransition(firstMessage.function(), firstMessage.payload());
        }
    }

    public void stop() throws InterruptedException {
        smInbox.stop();
        smOutbox.stop();
    }

    private void onStateTransition(String nextState, String message) {
        try {
            logger.info("FSM State change in {}: {}->{}({})",
                    queueName,
                    state == null ? "[null]" : state.name(),
                    nextState,
                    message);

            synchronized (this) {
                this.state = allStates.get(nextState);
                notifyAll();
            }

            if (!state.isFinal()) {
                var transition = state.next(message);
                smOutbox.notify(transition.state(), transition.message());
            }
        }
        catch (Exception e) {
            logger.error("Error in state machine transition", e);
            setErrorState();
        }
    }

    private void setErrorState() {
        synchronized (this) {
            state = errorState;
            notifyAll();
        }
    }

    private class StateEventSubscription implements MqSubscription {

        @Override
        public boolean filter(MqMessage rawMessage) {
            return true;
        }

        @Override
        public MqInboxResponse onRequest(MqMessage msg) {
            return null;
        }

        @Override
        public void onNotification(MqMessage msg) {
            onStateTransition(msg.function(), msg.payload());
        }
    }
}
