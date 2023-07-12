package nu.marginalia.mqsm;

import nu.marginalia.mq.MqFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.inbox.MqInboxIf;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSubscription;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mqsm.graph.ResumeBehavior;
import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;

/** A state machine that can be used to implement a finite state machine
 * using a message queue as the persistence layer.  The state machine is
 * resilient to crashes and can be resumed from the last state.
 */
public class StateMachine {
    private final Logger logger = LoggerFactory.getLogger(StateMachine.class);

    private final MqInboxIf smInbox;
    private final MqOutbox smOutbox;
    private final String queueName;
    private MachineState state;

    private final MachineState errorState = new StateFactory.ErrorState();
    private final MachineState finalState = new StateFactory.FinalState();
    private final MachineState resumingState = new StateFactory.ResumingState();

    private final List<BiConsumer<String, String>> stateChangeListeners = new ArrayList<>();

    private final Map<String, MachineState> allStates = new HashMap<>();

    /* The expectedMessageId guards against spurious state changes being triggered by old messages in the queue
     *
     * It contains the message id of the last message that was processed, and the messages sent by the state machine to
     * itself via the message queue all have relatedId set to expectedMessageId.  If the state machine is unitialized or
     * in a terminal state, it will accept messages with relatedIds that are equal to -1.
     * */
    private long expectedMessageId = -1;

    public StateMachine(MqFactory messageQueueFactory,
                        String queueName,
                        UUID instanceUUID,
                        AbstractStateGraph stateGraph) {
        this.queueName = queueName;

        smInbox = messageQueueFactory.createSynchronousInbox(queueName, instanceUUID);
        smOutbox = messageQueueFactory.createOutbox(queueName, queueName+"//out", instanceUUID);

        smInbox.subscribe(new StateEventSubscription());

        registerStates(List.of(errorState, finalState, resumingState));
        registerStates(stateGraph);

        for (var declaredState : stateGraph.declaredStates()) {
            if (!allStates.containsKey(declaredState)) {
                throw new IllegalArgumentException("State " + declaredState + " is not defined in the state graph");
            }
        }
    }

    /** Listen to state changes */
    public void listen(BiConsumer<String, String> listener) {
        stateChangeListeners.add(listener);
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
        smOutbox.notify(expectedMessageId, transition.state(), transition.message());
    }

    /** Initialize the state machine. */
    public void init(String jsonEncodedArgument) throws Exception {
        var transition = StateTransition.to("INITIAL", jsonEncodedArgument);

        synchronized (this) {
            this.state = allStates.get(transition.state());
            notifyAll();
        }

        smInbox.start();
        smOutbox.notify(expectedMessageId, transition.state(), transition.message());
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
        expectedMessageId = firstMessage.relatedId();

        if (firstMessage.state() == MqMessageState.NEW) {
            // The message is not acknowledged, so starting the inbox will trigger a state transition
            // We still need to set a state here so that the join() method works

            state = resumingState;
        } else if (resumeState.resumeBehavior().equals(ResumeBehavior.ERROR)) {
            // The message is acknowledged, but the state does not support resuming
            smOutbox.notify(expectedMessageId, "ERROR", "Illegal resumption from ACK'ed state " + firstMessage.function());
        } else {
            // The message is already acknowledged, so we replay the last state
            onStateTransition(firstMessage);
        }
    }

    public void stop() throws InterruptedException {
        smInbox.stop();
        smOutbox.stop();
    }

    private void onStateTransition(MqMessage msg) {
        final String nextState = msg.function();
        final String data = msg.payload();
        final long messageId = msg.msgId();
        final long relatedId = msg.relatedId();

        if (expectedMessageId != relatedId) {
            // We've received a message that we didn't expect, throwing an exception will cause it to be flagged
            // as an error in the message queue;  the message queue will proceed
            throw new IllegalStateException("Unexpected message id " + relatedId + ", expected " + expectedMessageId);
        }

        try {
            logger.info("FSM State change in {}: {}->{}({})",
                    queueName,
                    state == null ? "[null]" : state.name(),
                    nextState,
                    data);

            if (!allStates.containsKey(nextState)) {
                logger.error("Unknown state {}", nextState);
                setErrorState();
                return;
            }

            synchronized (this) {
                this.state = allStates.get(nextState);
                notifyAll();
            }

            if (!state.isFinal()) {
                var transition = state.next(msg.payload());

                expectedMessageId = messageId;
                smOutbox.notify(expectedMessageId, transition.state(), transition.message());
            }
            else {
                expectedMessageId = -1;
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
            onStateTransition(msg);
            try {
                stateChangeListeners.forEach(l -> l.accept(msg.function(), msg.payload()));
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
