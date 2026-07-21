package nu.marginalia.index.searchset;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import nu.marginalia.db.DomainRankingSetsService;
import nu.marginalia.index.IndexFactory;
import nu.marginalia.ranking.connectivity.ConnectivityMapFile;
import nu.marginalia.ranking.connectivity.ConnectivityView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

@Singleton
public class ConnectivitySets {

    private static final Logger logger = LoggerFactory.getLogger(ConnectivitySets.class);

    private final Path filePath;

    private volatile Int2ByteOpenHashMap connectivity = new Int2ByteOpenHashMap();

    @Inject
    public ConnectivitySets(IndexFactory indexFactory) {
        filePath = DomainRankingSetsService.setFileName(indexFactory.getSearchSetsBase(), ConnectivityMapFile.FILE_NAME);

        reload();
    }

    public ConnectivityView getView() {
        return new ConnectivityView(connectivity);
    }

    public void reload() {
        try {
            connectivity = ConnectivityMapFile.read(filePath);
        }
        catch (IOException ex) {
            logger.error("Failed to load connectivity set");
        }
        logger.info("Loaded {} connectivity entries", connectivity.size());
    }

}
