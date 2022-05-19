package nu.marginalia.wmsa.smhi.scraper.crawler;

import com.google.gson.*;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.SneakyThrows;
import nu.marginalia.wmsa.smhi.model.Plats;
import nu.marginalia.wmsa.smhi.model.PrognosData;
import nu.marginalia.wmsa.smhi.scraper.PlatsReader;
import nu.marginalia.wmsa.smhi.scraper.crawler.entity.SmhiEntityStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SmhiCrawler {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Gson gson;
    private SmhiBackendApi api;
    private SmhiEntityStore store;
    private final List<Plats> platser;
    private Disposable job;

    @Inject @SneakyThrows
    public SmhiCrawler(SmhiBackendApi backendApi, SmhiEntityStore store, PlatsReader platsReader) {
        this.api = backendApi;
        this.store = store;
        this.platser = platsReader.readPlatser();

        class LocalDateAdapter implements JsonDeserializer<LocalDateTime> {
            @Override
            public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                return LocalDateTime
                        .parse(json.getAsString(), DateTimeFormatter.ISO_ZONED_DATE_TIME);
            }
        }

        gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateAdapter())
                .create();
    }

    public void start() {
        job = Observable
                .fromIterable(new ArrayList<>(platser))
                .subscribeOn(Schedulers.io())
                .filter(this::isNeedsUpdate)
                .take(5)
                .flatMapMaybe(this::hamtaData)
                .repeatWhen(this::repeatDelay)
                .doOnError(this::handleError)
                .subscribe(store::offer);
    }
    public void stop() {
        Optional.ofNullable(job).ifPresent(Disposable::dispose);
    }

    private Observable<?> repeatDelay(Observable<Object> completed) {
        return completed.delay(1, TimeUnit.SECONDS);
    }

    protected void handleError(Throwable throwable) {
        logger.error("Caught error", throwable);
    }

    public Maybe<PrognosData> hamtaData(Plats plats) {
        try {
            var data = api.hamtaData(plats);

            PrognosData model = gson.fromJson(data.jsonContent, PrognosData.class);

            model.expires = data.expiryDate;
            model.plats = plats;

            return Maybe.just(model);
        }
        catch (Exception ex) {
            logger.error("Failed to fetch data", ex);
            return Maybe.empty();
        }
    }


    boolean isNeedsUpdate(Plats plats) {
        var prognos = store.prognos(plats);

        if (null == prognos) {
            return true;
        }

        LocalDateTime crawlTime = LocalDateTime.parse(prognos.crawlTime);
        return crawlTime.plusHours(1).isBefore(LocalDateTime.now());
    }

}
