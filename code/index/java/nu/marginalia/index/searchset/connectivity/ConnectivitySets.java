package nu.marginalia.index.searchset.connectivity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.zaxxer.hikari.HikariDataSource;
import it.unimi.dsi.fastutil.ints.*;
import nu.marginalia.api.linkgraph.AggregateLinkGraphClient;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.index.IndexFactory;
import nu.marginalia.index.searchset.RankingSearchSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;

@Singleton
public class ConnectivitySets {

    private static final Logger logger = LoggerFactory.getLogger(ConnectivitySets.class);

    private final AggregateLinkGraphClient graphClient;
    private final IndexFactory indexFactory;
    private final HikariDataSource dataSource;
    private final String sourceSetName;
    private final String dataFileName;

    private final Path filePath;

    private volatile Int2ByteOpenHashMap connectivity = new Int2ByteOpenHashMap();

    @Inject
    public ConnectivitySets(AggregateLinkGraphClient graphClient,
                            IndexFactory indexFactory,
                            HikariDataSource dataSource
                            ) {
        this.graphClient = graphClient;
        this.indexFactory = indexFactory;
        this.dataSource = dataSource;
        this.sourceSetName = "SMALL";
        this.dataFileName = "__CONNECTIVITY_MAP";

        filePath = DomainRankingSetsService.setFileName(indexFactory.getSearchSetsBase(), dataFileName);

        load();
    }

    public ConnectivityView getView() {
        return new ConnectivityView(connectivity);
    }

    public void recalculate() {
        Int2ObjectOpenHashMap<IntList> outgoingLinks = new Int2ObjectOpenHashMap<>();
        Int2ObjectOpenHashMap<IntList> inboudLinks = new Int2ObjectOpenHashMap<>();
        Int2IntOpenHashMap linkCount = new Int2IntOpenHashMap();

        IntOpenHashSet indexedDomains = new IntOpenHashSet();

        try {
            // Load all relevant IDs so we can exclude links to non-indexed domains

            try (var conn = dataSource.getConnection();
                 var stmt = conn.prepareStatement("SELECT ID FROM EC_DOMAIN WHERE NODE_AFFINITY>0")) {
                stmt.setFetchSize(10_000);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    indexedDomains.add(rs.getInt(1));
                }
            }

            // Load the link graph
            var iter = graphClient.getAllDomainLinks().iterator();
            while (iter.advance()) {
                int src = iter.source();
                int dest = iter.dest();

                if (indexedDomains.contains(src) && indexedDomains.contains(dest)) {
                    inboudLinks.computeIfAbsent(src, v -> new IntArrayList()).add(dest);
                    outgoingLinks.computeIfAbsent(dest, v -> new IntArrayList()).add(src);
                }
            }

            var originSet = new RankingSearchSet(sourceSetName,
                    DomainRankingSetsService.setFileName(indexFactory.getSearchSetsBase(), sourceSetName));

            var idsList = originSet.domainIds();

            IntSet direct = new IntOpenHashSet(idsList);
            IntSet reachable = new IntOpenHashSet();
            IntSet reachable2 = new IntOpenHashSet();
            IntSet linking = new IntOpenHashSet();
            IntSet linking2 = new IntOpenHashSet();
            IntSet bidi = new IntOpenHashSet();
            IntSet bidi2 = new IntOpenHashSet();

            for (int id : idsList) {
                var outgoing = outgoingLinks.getOrDefault(id, IntList.of());
                for (int outId: outgoing) linkCount.addTo(outId, 1);

                var inbound = inboudLinks.getOrDefault(id, IntList.of());
                for (int inId: inbound) linkCount.addTo(inId, 1);

                reachable.addAll(inbound);
                linking.addAll(outgoing);
            }

            reachable.removeAll(direct);
            linking.removeAll(direct);

            bidi.addAll(reachable);
            bidi.retainAll(linking);

            reachable2.addAll(reachable);
            linking2.addAll(linking);
            bidi2.addAll(bidi);

            reachable2.removeIf(id -> linkCount.get(id) < 5);
            linking2.removeIf(id -> linkCount.get(id) < 5);
            bidi2.removeIf(id -> linkCount.get(id) < 5);

            Int2ByteOpenHashMap newConnectivity = new Int2ByteOpenHashMap();

            direct.forEach(id -> newConnectivity.put(id, (byte) DomainSetConnectivity.DIRECT.ordinal()));

            bidi2.forEach(id -> newConnectivity.putIfAbsent(id, (byte) DomainSetConnectivity.BIDI_HOT.ordinal()));
            reachable2.forEach(id -> newConnectivity.putIfAbsent(id, (byte) DomainSetConnectivity.REACHABLE_HOT.ordinal()));
            linking2.forEach(id -> newConnectivity.putIfAbsent(id, (byte) DomainSetConnectivity.LINKING_HOT.ordinal()));

            bidi.forEach(id -> newConnectivity.putIfAbsent(id, (byte) DomainSetConnectivity.BIDI.ordinal()));
            reachable.forEach(id -> newConnectivity.putIfAbsent(id, (byte) DomainSetConnectivity.REACHABLE.ordinal()));
            linking.forEach(id -> newConnectivity.putIfAbsent(id, (byte) DomainSetConnectivity.LINKING.ordinal()));

            connectivity = newConnectivity;
            write();
        }
        catch (SQLException | IOException ex) {
            logger.error("Failed to calculate conenctivity sets", ex);
        }
    }

    private void load() {
        try ( var ds = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(filePath, StandardOpenOption.READ))
        )) {
            for (;;) {
                int id = ds.readInt();
                byte code = ds.readByte();

                connectivity.put(id, code);
            }
        }
        catch (EOFException ex) {
            //
        }
        catch (IOException ex) {
            logger.error("Failed to load connectivity set");
        }
        logger.info("Loaded {} connectivity entries", connectivity.size());
    }

    private void write() throws IOException {
        try (var ds = new DataOutputStream(
                new BufferedOutputStream(
                Files.newOutputStream(filePath,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))
        ))
        {
            for (var entry : connectivity.int2ByteEntrySet()) {
                ds.writeInt(entry.getIntKey());
                ds.writeByte(entry.getByteValue());
            }
        }
    }

}
