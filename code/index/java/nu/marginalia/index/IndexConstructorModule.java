package nu.marginalia.index;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import nu.marginalia.IndexLocations;
import nu.marginalia.index.searchset.DomainRankings;
import nu.marginalia.storage.FileStorageService;

public class IndexConstructorModule extends AbstractModule {
    @Override
    public void configure() {
    }

    @Provides @Singleton
    public DomainRankings getDomainRankings(FileStorageService fileStorageService) {
        var rankings = new DomainRankings();

        rankings.load(IndexLocations.getSearchSetsPath(fileStorageService));

        return rankings;
    }
}
