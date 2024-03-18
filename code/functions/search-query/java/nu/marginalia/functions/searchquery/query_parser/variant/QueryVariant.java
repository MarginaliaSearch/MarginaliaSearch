package nu.marginalia.functions.searchquery.query_parser.variant;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@AllArgsConstructor
@Getter
@ToString
@EqualsAndHashCode
public class QueryVariant {
    public final List<String> terms;
    public final double value;
}
