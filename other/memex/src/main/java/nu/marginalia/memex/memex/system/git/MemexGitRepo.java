package nu.marginalia.memex.memex.system.git;

import nu.marginalia.memex.memex.model.MemexNodeUrl;

public interface MemexGitRepo {
    void pull();

    void remove(MemexNodeUrl url);

    void add(MemexNodeUrl url);

    void update(MemexNodeUrl url);

    void rename(MemexNodeUrl src, MemexNodeUrl dst);
}
