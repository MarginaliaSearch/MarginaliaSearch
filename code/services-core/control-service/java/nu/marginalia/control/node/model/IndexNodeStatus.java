package nu.marginalia.control.node.model;

import nu.marginalia.nodecfg.model.NodeConfiguration;

public record IndexNodeStatus(NodeConfiguration configuration, boolean indexServiceOnline) {
    public int id() {
        return configuration.node();
    }
}
