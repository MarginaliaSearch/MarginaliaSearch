package nu.marginalia.api.searchquery.model.results.debug;

import java.util.List;

public record ResultRankingInputs(int rank, int asl, int quality, int size, int topology, int year, List<String> flags) {}
