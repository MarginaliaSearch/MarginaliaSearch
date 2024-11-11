package nu.marginalia.control.actor;


import com.google.inject.Inject;
import nu.marginalia.nodecfg.NodeConfigurationService;
import nu.marginalia.nodecfg.model.NodeConfiguration;

import java.util.OptionalInt;
import java.util.stream.IntStream;

/** Encodes the logic for which nodes should be used in
 * control-side actors that precess across each node */
public class PrecessionNodes {

    private final NodeConfigurationService nodeConfigurationService;

    @Inject
    public PrecessionNodes(NodeConfigurationService nodeConfigurationService) {
        this.nodeConfigurationService = nodeConfigurationService;
    }

    /** Returns the first node that should be used in the precession, or
     * OptionalInt.empty() if no nodes are eligible.
     */
    public OptionalInt first() {
        return eligibleNodes().findFirst();
    }

    /** Returns the next node that should be used in the precession after the provided id,
     * or OptionalInt.empty() if no such node exists.
     */
    public OptionalInt next(int current) {
        return eligibleNodes()
                .filter(i -> i > current)
                .findFirst();
    }

    private IntStream eligibleNodes() {
        return nodeConfigurationService.getAll().stream()
                .filter(NodeConfiguration::includeInPrecession)
                .mapToInt(NodeConfiguration::node)
                .sorted();
    }
}
