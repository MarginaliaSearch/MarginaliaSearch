package nu.marginalia.control.model;

import nu.marginalia.actor.prototype.AbstractActorPrototype;
import nu.marginalia.actor.state.ActorState;
import nu.marginalia.actor.state.ActorStateInstance;

import java.util.*;

public record ActorStateGraph(String description, List<nu.marginalia.control.model.ActorState> states) {

    public ActorStateGraph(AbstractActorPrototype graph, ActorStateInstance currentState) {
        this(graph.describe(), getStateList(graph, currentState));
    }

    private static List<nu.marginalia.control.model.ActorState> getStateList(
            AbstractActorPrototype graph,
            ActorStateInstance currentState)
    {
        Map<String, ActorState> declaredStates = graph.declaredStates();
        Set<ActorState> seenStates = new HashSet<>(declaredStates.size());
        LinkedList<ActorState> edge = new LinkedList<>();

        List<nu.marginalia.control.model.ActorState> statesList = new ArrayList<>(declaredStates.size());

        edge.add(declaredStates.get("INITIAL"));

        while (!edge.isEmpty()) {
            var first = edge.removeFirst();
            if (first == null || !seenStates.add(first)) {
                continue;
            }
            statesList.add(new nu.marginalia.control.model.ActorState(first, currentState.name().equals(first.name())));

            edge.add(declaredStates.get(first.next()));

            for (var transition : first.transitions()) {
                edge.add(declaredStates.get(transition));
            }
        }

        if (!declaredStates.containsKey("ERROR")) {
            statesList.add(new nu.marginalia.control.model.ActorState("ERROR", currentState.name().equals("ERROR"), List.of(), "Terminal error state"));
        }
        if (!declaredStates.containsKey("END")) {
            statesList.add(new nu.marginalia.control.model.ActorState("END", currentState.name().equals("END"), List.of(), "The machine terminated successfully"));
        }

        return statesList;
    }
}
