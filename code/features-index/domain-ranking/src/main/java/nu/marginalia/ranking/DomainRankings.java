package nu.marginalia.ranking;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ShortOpenHashMap;
import nu.marginalia.model.id.UrlIdCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class DomainRankings {
    private final Int2ShortOpenHashMap rankings;
    private static final Logger logger = LoggerFactory.getLogger(DomainRankings.class);

    private final int MAX_MEANINGFUL_RANK = 50_000;
    private final int MAX_RANK_VALUE = 255;
    private final int MIN_RANK_VALUE = 1;
    private final double RANK_SCALING_FACTOR = (double) MAX_RANK_VALUE / MAX_MEANINGFUL_RANK;

    public DomainRankings() {
        rankings = new Int2ShortOpenHashMap();
    }
    public DomainRankings(Int2IntOpenHashMap values) {
         rankings = new Int2ShortOpenHashMap(values.size());
         values.forEach(this::putRanking);
    }

    private static final String name = "_rankings.dat";

    public void save(Path basePath) {
        Path fileName = basePath.resolve(name);

        logger.info("Saving domain rankings to {}", fileName);

        try (DataOutputStream dos = new DataOutputStream(Files.newOutputStream(fileName,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)))
        {
            rankings.forEach((domainId, rank) -> {
                try {
                    dos.writeInt(domainId);
                    dos.writeShort(rank);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void load(Path basePath) {
        Path fileName = basePath.resolve(name);

        logger.info("Loading domain rankings from {}", fileName);

        try (DataInputStream dis = new DataInputStream(Files.newInputStream(fileName))) {
            rankings.clear();
            for (;;) {
                int domainId = dis.readInt();
                short rank = dis.readShort();
                rankings.put(domainId, rank);
            }
        }
        catch (EOFException e) {
            // ok
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void putRanking(int domainId, int value) {
        rankings.put(domainId, scaleRank(value));
    }

    private short scaleRank(int value) {
        double rankScaled = RANK_SCALING_FACTOR * value;
        return (short) min(MAX_RANK_VALUE, max(MIN_RANK_VALUE, rankScaled));
    }

    public int getRanking(int domainId) {
        return rankings.getOrDefault(domainId, (short) MAX_RANK_VALUE);
    }

    public float getSortRanking(long docId) {
        int domainId = UrlIdCodec.getDomainId(docId);
        return rankings.getOrDefault(domainId, (short) MAX_RANK_VALUE) / (float) MAX_RANK_VALUE;
    }

    public int size() {
        return rankings.size();
    }
}
