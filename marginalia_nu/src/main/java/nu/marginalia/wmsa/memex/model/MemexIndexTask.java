package nu.marginalia.wmsa.memex.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor @ToString @Getter
public class MemexIndexTask {
    public final String task;
    public final String taskId;
    public final String url;
    public final String type;
}
