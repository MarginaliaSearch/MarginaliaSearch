package nu.marginalia.search.client.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor @Getter
public class ApiSearchResult {
    public String url;
    public String title;
    public String description;
    public double quality;

    public List<List<ApiSearchResultQueryDetails>> details = new ArrayList<>();

}
