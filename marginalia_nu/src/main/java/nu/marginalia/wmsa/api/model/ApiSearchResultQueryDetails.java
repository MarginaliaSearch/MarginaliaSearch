package nu.marginalia.wmsa.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;

@AllArgsConstructor @Getter
public class ApiSearchResultQueryDetails {

    String keyword;
    int tfIdf;
    int count;

    Set<String> flagsUnstableAPI;
}
