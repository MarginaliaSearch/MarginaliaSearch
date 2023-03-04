package nu.marginalia.memex.memex.system.git;

import com.google.inject.Singleton;
import nu.marginalia.memex.memex.model.MemexNodeUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MemexGitRepoDummy implements MemexGitRepo {
    private static final Logger logger = LoggerFactory.getLogger(MemexGitRepoDummy.class);

    @Override
    public void pull() {
        logger.info("Would perform a pull here");
    }

    @Override
    public void remove(MemexNodeUrl url) {
        logger.info("Would perform a remove here");
    }

    @Override
    public void add(MemexNodeUrl url) {
        logger.info("Would perform an add here");
    }

    @Override
    public void update(MemexNodeUrl url) {
        logger.info("Would perform an update here");
    }

    @Override
    public void rename(MemexNodeUrl src, MemexNodeUrl dst) {
        logger.info("Would perform a rename here");
    }
}
