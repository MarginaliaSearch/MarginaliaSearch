package nu.marginalia.memex.gemini;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public class BadBotList {
    private final Set<InetAddress> shitlist = new HashSet<>();
    public static final BadBotList INSTANCE = new BadBotList();
    private final Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    private BadBotList() {}

    public boolean isAllowed(InetAddress address) {
        return !shitlist.contains(address);
    }

    public boolean isQueryPermitted(InetAddress address, String query) {
        if (isBadQuery(query)) {
            logger.info("Banning {}", address);
            shitlist.add(address);
            return false;
        }
        return true;
    }

    private boolean isBadQuery(String query) {
        if (query.startsWith("GET")) {
            return true;
        }
        if (query.startsWith("OPTIONS")) {
            return true;
        }
        if (query.contains("mstshash")) {
            return true;
        }

        return false;
    }
}
