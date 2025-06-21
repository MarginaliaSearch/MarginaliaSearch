package nu.marginalia.ndp;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import nu.marginalia.nodecfg.NodeConfigurationService;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

/** DomainAllocator is responsible for assigning domains to partitions/nodes.
 * This is ensured to make sure that domains are evenly distributed across the nodes.
 */
public class DomainNodeAllocator {

    private final NodeConfigurationService nodeConfigurationService;
    private final HikariDataSource dataSource;

    private record NodeCount(int nodeId, int count)
        implements Comparable<NodeCount>
    {
        public NodeCount incrementCount() {
            return new NodeCount(nodeId, count + 1);
        }

        @Override
        public int compareTo(@NotNull DomainNodeAllocator.NodeCount o) {
            return Integer.compare(this.count, o.count);
        }
    }

    private final PriorityQueue<NodeCount> countPerNode = new PriorityQueue<>();
    volatile boolean initialized = false;

    @Inject
    public DomainNodeAllocator(NodeConfigurationService nodeConfigurationService, HikariDataSource dataSource) {
        this.nodeConfigurationService = nodeConfigurationService;
        this.dataSource = dataSource;

        Thread.ofPlatform()
                .name("DomainNodeAllocator::initialize()")
                .start(this::initialize);
    }


    public void initialize() {
        if (initialized) return;

        Set<Integer> viableNodes = new HashSet<>();

        // Find all viable nodes that can handle batch crawls
        for (var node : nodeConfigurationService.getAll()) {
            if (node.disabled())
                continue;
            if (node.profile().permitBatchCrawl())
                viableNodes.add(node.node());
        }

        // Fetch the current counts of domains per node from the database
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT COUNT(*) AS CNT, NODE_AFFINITY
                FROM EC_DOMAIN
                WHERE NODE_AFFINITY>0
                GROUP BY NODE_AFFINITY
                """))
        {

            var rs = stmt.executeQuery();
            while (rs.next()) {

                int nodeId = rs.getInt("NODE_AFFINITY");
                int count = rs.getInt("CNT");

                if (viableNodes.remove(nodeId)) {
                    countPerNode.add(new NodeCount(nodeId, count));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load domain counts from database", e);
        }

        // Add any remaining viable nodes that were not found in the database
        for (int nodeId : viableNodes) {
            countPerNode.add(new NodeCount(nodeId, 0));
        }

        initialized = true;
    }

    private void ensureInitialized() {
        if (initialized) return;

        synchronized (this) {
            while (!initialized) {
                try {
                    // Wait until the initialization is complete
                    this.wait(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("DomainAllocator initialization interrupted", e);
                }
            }
        }
    }

    public synchronized int totalCount() {
        ensureInitialized();
        return countPerNode.stream().mapToInt(NodeCount::count).sum();
    }

    /** Returns the next node ID to assign a domain to.
     * This method is synchronized to ensure thread safety when multiple threads are allocating domains.
     * The node ID returned is guaranteed to be one of the viable nodes configured in the system.
     */
    public synchronized int nextNodeId() {
        ensureInitialized();

        // Synchronized is fine here as this is not a hot path
        // (and PriorityBlockingQueue won't help since we're re-adding the same element with a new count all the time)

        NodeCount allocation = countPerNode.remove();
        countPerNode.add(allocation.incrementCount());
        return allocation.nodeId();
    }
}
