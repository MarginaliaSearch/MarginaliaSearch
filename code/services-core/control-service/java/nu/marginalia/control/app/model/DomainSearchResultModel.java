package nu.marginalia.control.app.model;

import java.util.List;
import java.util.Map;

public record DomainSearchResultModel(String query,
                                      String affinity,
                                      String field,
                                      Map<String, Boolean> selectedAffinity,
                                      Map<String, Boolean> selectedField,
                                      int page,
                                      boolean hasNext,
                                      boolean hasPrevious,
                                      List<Integer> nodes,
                                      List<DomainModel> results)
{
    public Integer getNextPage() {
        if (!hasNext) return null;
        return page + 1;
    }

    public Integer getPreviousPage() {
        if (!hasPrevious) return null;
        return page - 1;
    }
}
