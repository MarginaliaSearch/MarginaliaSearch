package nu.marginalia.control.model;

import nu.marginalia.mqsm.graph.AbstractStateGraph;
import nu.marginalia.mqsm.graph.GraphState;
import nu.marginalia.mqsm.state.MachineState;

import java.util.*;
import java.util.stream.Collectors;

public record ActorStateGraph(List<ActorState> states) {

    public ActorStateGraph(AbstractStateGraph graph, MachineState currentState) {
        this(getStateList(graph, currentState));
    }

    private static List<ActorState> getStateList(
            AbstractStateGraph graph,
            MachineState currentState)
    {
        Map<String, GraphState> declaredStates = graph.declaredStates().stream().collect(Collectors.toMap(GraphState::name, gs -> gs));
        Set<GraphState> seenStates = new HashSet<>(declaredStates.size());
        LinkedList<GraphState> edge = new LinkedList<>();

        List<ActorState> statesList = new ArrayList<>(declaredStates.size());

        edge.add(declaredStates.get("INITIAL"));

        while (!edge.isEmpty()) {
            var first = edge.removeFirst();
            if (first == null || !seenStates.add(first)) {
                continue;
            }
            statesList.add(new ActorState(first, currentState.name().equals(first.name())));

            edge.add(declaredStates.get(first.next()));

            for (var transition : first.transitions()) {
                edge.add(declaredStates.get(transition));
            }
        }

        if (!declaredStates.containsKey("ERROR")) {
            statesList.add(new ActorState("ERROR", currentState.name().equals("ERROR"), List.of(), "Terminal error state"));
        }
        if (!declaredStates.containsKey("END")) {
            statesList.add(new ActorState("END", currentState.name().equals("END"), List.of(), "The machine terminated successfully"));
        }

        return statesList;
    }
}
