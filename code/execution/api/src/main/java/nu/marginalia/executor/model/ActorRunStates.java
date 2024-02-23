package nu.marginalia.executor.model;

import java.util.List;

public record ActorRunStates(int node, List<ActorRunState> states) {}
