package nu.marginalia.index.config;

import java.util.List;

public class RankingSettingsEntry {
    /** Bias the ranking toward these domains */
    public List<String> domains;

    /** Number of domains to include in ranking */
    public int max;
}
