package nu.marginalia.search.command;



import java.util.Optional;

public interface SearchCommandInterface {
    Optional<Object> process(SearchParameters parameters);
}
