package nu.marginalia.wmsa.edge.index.model;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
public class EdgeIndexSearchTerms {
    public List<Integer> includes = new ArrayList<>();
    public List<Integer> excludes = new ArrayList<>();

    public boolean isEmpty() {
        return includes.isEmpty();
    }
}
