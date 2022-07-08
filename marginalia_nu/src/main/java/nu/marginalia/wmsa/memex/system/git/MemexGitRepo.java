package nu.marginalia.wmsa.memex.system.git;

import nu.marginalia.wmsa.memex.model.MemexNodeUrl;

public interface MemexGitRepo {
    void pull();

    void remove(MemexNodeUrl url);

    void add(MemexNodeUrl url);

    void update(MemexNodeUrl url);

    void rename(MemexNodeUrl src, MemexNodeUrl dst);
}
