package nu.marginalia.actor;

import nu.marginalia.actor.prototype.ActorPrototype;
import nu.marginalia.mq.MessageQueueFactory;
import nu.marginalia.mq.MqMessage;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.inbox.MqInboxResponse;
import nu.marginalia.mq.inbox.MqSubscription;
import nu.marginalia.mq.inbox.MqSynchronousInbox;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.actor.state.ActorResumeBehavior;
import nu.marginalia.actor.state.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

/** A state machine that can be used to implement an actor
 * using a message queue as the persistence layer.  The state machine is
 * resilient to crashes and can be resumed from the last state.
 */
public class ActorStateMachine {
    private final Logger logger = LoggerFactory.getLogger(ActorStateMachine.class);

    private final MqSynchronousInbox smInbox;
    private final MqOutbox smOutbox;
    private final String queueName;


    private volatile ActorStateInstance state;
    private volatile ExpectedMessage expectedMessage = ExpectedMessage.anyUnrelated();


    private final ActorStateInstance errorState = new ActorStateFactory.ErrorStateInstance();
    private final ActorStateInstance finalState = new ActorStateFactory.FinalState();
    private final ActorStateInstance resumingState = new ActorStateFactory.ResumingState();

    private final List<BiConsumer<String, String>> stateChangeListeners = new ArrayList<>();
    private final Map<String, ActorStateInstance> allStates = new HashMap<>();

    private final boolean isDirectlyInitializable;

    public ActorStateMachine(MessageQueueFactory messageQueueFactory,
                             String queueName,
                             UUID instanceUUID,
                             ActorPrototype statePrototype)
    {
        this.queueName = queueName;

        smInbox = messageQueueFactory.createSynchronousInbox(queueName, instanceUUID);
        smOutbox = messageQueueFactory.createOutbox(queueName, queueName+"//out", instanceUUID);

        smInbox.subscribe(new StateEventSubscription());

        registerStates(List.of(errorState, finalState, resumingState));
        registerStates(statePrototype);
        isDirectlyInitializable = statePrototype.isDirectlyInitializable();

        statePrototype.declaredStates().forEach((name, declaredState) -> {
            if (!allStates.containsKey(name)) {
                throw new IllegalArgumentException("State " + name + " is not defined in the state graph");
            }
            if (!allStates.containsKey(declaredState.next())) {
                throw new IllegalArgumentException("State " + declaredState.next() + " is not defined in the state graph");
            }
            for (var state : declaredState.transitions()) {
                if (!allStates.containsKey(state)) {
                    throw new IllegalArgumentException("State " + state + " is not defined in the state graph");
                }
            }
        });

        resume();

        smInbox.start();
    }

    /** Listen to state changes */
    public void listen(BiConsumer<String, String> listener) {
        stateChangeListeners.add(listener);
    }

    /** Register the state graph */
    void registerStates(List<ActorStateInstance> states) {
        for (var state : states) {
            allStates.put(state.name(), state);
        }
    }

    /** Register the state graph */
    void registerStates(ActorPrototype states) {
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

    /** Wait for the state machine to reach a final state up to a given timeout.
     */
    public void join(long timeout, TimeUnit timeUnit) throws InterruptedException, TimeoutException {
        long deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout);

        synchronized (this) {
            if (null == state)
                return;

            while (!state.isFinal()) {
                if (deadline <= System.currentTimeMillis())
                    throw new TimeoutException("Timeout waiting for state machine to reach final state");
                wait(100);
            }
        }
    }

    /** Initialize the state machine. */
    public void init() throws Exception {
        var transition = ActorStateTransition.to("INITIAL");

        synchronized (this) {
            this.state = allStates.get(transition.state());
            notifyAll();
        }

        smOutbox.sendNotice(transition.state(), transition.message());
    }

    /** Initialize the state machine. */
    public void initFrom(String firstState) throws Exception {
        var transition = ActorStateTransition.to(firstState);

        synchronized (this) {
            this.state = allStates.get(transition.state());
            notifyAll();
        }

        smOutbox.sendNotice(transition.state(), transition.message());
    }

    /** Initialize the state machine. */
    public void init(String jsonEncodedArgument) throws Exception {
        var transition = ActorStateTransition.to("INITIAL", jsonEncodedArgument);

        synchronized (this) {
            this.state = allStates.get(transition.state());
            notifyAll();
        }

        smOutbox.sendNotice(transition.state(), transition.message());
    }

    /** Initialize the state machine. */
    public void initFrom(String state, String jsonEncodedArgument) throws Exception {
        var transition = ActorStateTransition.to(state, jsonEncodedArgument);

        synchronized (this) {
            this.state = allStates.get(transition.state());
            notifyAll();
        }

        smOutbox.sendNotice(transition.state(), transition.message());
    }

    /** Resume the state machine from the last known state. */
    private void resume() {

        // We only permit resuming from the unitialized state
        if (state != null) {
            return;
        }

        // Fetch the last messages from the inbox
        var message = smInbox.replay(5)
                .stream()
                .filter(m -> (m.state() == MqMessageState.NEW) || (m.state() == MqMessageState.ACK))
                .findFirst();

        if (message.isEmpty()) {
            // No messages in the inbox, so start in a terminal state
            expectedMessage = ExpectedMessage.anyUnrelated();
            state = finalState;
            return;
        }

        var firstMessage = message.get();
        var resumeState = allStates.get(firstMessage.function());

        logger.info("Resuming state machine from {}({})/{}", firstMessage.function(), firstMessage.payload(), firstMessage.state());
        expectedMessage = ExpectedMessage.expectThis(firstMessage);

        if (firstMessage.state() == MqMessageState.NEW) {
            // The message is not acknowledged, so starting the inbox will trigger a state transition
            // We still need to set a state here so that the join() method works

            state = resumingState;
        }
        else if (firstMessage.state() == MqMessageState.ACK) {
            resumeFromAck(resumeState, firstMessage);
        }
    }

    private void resumeFromAck(ActorStateInstance resumeState,
                               MqMessage message)
    {
        try {
            if (resumeState.resumeBehavior().equals(ActorResumeBehavior.ERROR)) {
                // The message is acknowledged, but the state does not support resuming
                smOutbox.sendNotice(expectedMessage.id, "ERROR", "Illegal resumption from ACK'ed state " + message.function());
            }
            else if (resumeState.resumeBehavior().equals(ActorResumeBehavior.RESTART)) {
                this.state = resumeState;

                // The message is already acknowledged, we flag it as dead and then send an identical message
                smOutbox.flagAsDead(message.msgId());
                expectedMessage = ExpectedMessage.responseTo(message);
                smOutbox.sendNotice(message.msgId(), "INITIAL", "");
            }
            else {
                this.state = resumeState;

                // The message is already acknowledged, we flag it as dead and then send an identical message
                smOutbox.flagAsDead(message.msgId());
                expectedMessage = ExpectedMessage.responseTo(message);
                smOutbox.sendNotice(message.msgId(), message.function(), message.payload());
            }
        }
        catch (Exception e) {
            logger.error("Failed to replay state", e);
        }
    }

    public void stop() throws InterruptedException {
        smInbox.stop();
        smOutbox.stop();
    }

    private void onStateTransition(MqMessage msg) {
        final String nextState = msg.function();
        final String data = msg.payload();

        final long relatedId = msg.relatedId();

        if (!expectedMessage.isExpected(msg)) {
            // We've received a message that we didn't expect, throwing an exception will cause it to be flagged
            // as an error in the message queue;  the message queue will proceed

            throw new IllegalStateException("Unexpected message id " + relatedId + ", expected " + expectedMessage.id);
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
                logger.info("Transitining from state {}", state.name());
                var transition = state.next(msg.payload());

                if (!expectedMessage.isExpected(msg)) {
                    logger.warn("Expected message changed during execution, skipping state transition to {}", transition.state());
                }
                else {
                    expectedMessage = ExpectedMessage.responseTo(msg);
                    smOutbox.sendNotice(expectedMessage.id, transition.state(), transition.message());
                }
            }
            else {
                // On terminal transition, we expect any message
                expectedMessage = ExpectedMessage.anyUnrelated();
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

    public ActorStateInstance getState() {
        return state;
    }

    public void abortExecution() throws Exception {
        // Create a fake message to abort the execution
        // This helps make sense of the queue when debugging
        // and also permits the real termination message to have an
        // unique expected ID

        long abortMsgId = smOutbox.sendNotice(expectedMessage.id, "ABORT", "Aborting execution");

        // Set it as dead to clean up the queue from mystery ACK messages
        smOutbox.flagAsDead(abortMsgId);

        // Set the expected message to the abort message,
        // technically there's a slight chance of a race condition here,
        // which will cause this message to be ERR'd and the process to
        // continue, but it's very unlikely and the worst that can happen
        // is you have to abort twice.

        expectedMessage = ExpectedMessage.expectId(abortMsgId);

        // Add a state transition to the final state
        smOutbox.sendNotice(abortMsgId, finalState.name(), "");

        // Dislodge the current task with an interrupt.
        // It's actually fine if we accidentally interrupt the wrong thread
        // (i.e. the abort task), since it shouldn't be doing anything interruptable
        smInbox.abortCurrentTask();
    }

    /** Returns true if there is an INITIAL state that requires no parameters */
    public boolean isDirectlyInitializable() {
        return isDirectlyInitializable;
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
                // Rethrowing this will flag the message as an error in the message queue
                throw new RuntimeException("Error in state change listener", ex);
            }
        }
    }
}

