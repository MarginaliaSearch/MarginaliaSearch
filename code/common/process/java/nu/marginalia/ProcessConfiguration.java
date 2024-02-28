package nu.marginalia;

import java.util.UUID;

public record ProcessConfiguration(String processName, int node, UUID instanceUuid) {

}
