package nu.marginalia.wmsa.edge.search.svc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import nu.marginalia.wmsa.configuration.server.Context;
import nu.marginalia.wmsa.edge.assistant.dict.WikiArticles;
import nu.marginalia.wmsa.encyclopedia.EncyclopediaClient;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Future;

@Singleton
public class EdgeSearchWikiArticlesService {
    private final EncyclopediaClient encyclopediaClient;

    @Inject
    public EdgeSearchWikiArticlesService(EncyclopediaClient encyclopediaClient) {
        this.encyclopediaClient = encyclopediaClient;
    }

    @NotNull
    public Future<WikiArticles> getWikiArticle(Context ctx, String humanQuery) {

        if (!encyclopediaClient.isAlive()) {
            return Observable.just(new WikiArticles()).toFuture();
        }

        return encyclopediaClient
                .encyclopediaLookup(ctx,
                        humanQuery.replaceAll("\\s+", "_")
                                .replaceAll("\"", "")
                )
                .subscribeOn(Schedulers.io())
                .onErrorReturn(e -> new WikiArticles())
                .toFuture()
                ;
    }

}
