package nu.marginalia.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@AllArgsConstructor @Getter
public class ApiSearchResultQueryDetails {

    String keyword;
    int count;

    Set<String> flagsUnstableAPI;
}
