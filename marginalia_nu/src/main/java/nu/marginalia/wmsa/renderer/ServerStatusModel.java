package nu.marginalia.wmsa.renderer;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public class ServerStatusModel {
    public final String server;
    public final String status;
}
