package nu.marginalia.wmsa.smhi.scraper.crawler.entity;

import com.google.inject.Singleton;
import io.reactivex.rxjava3.subjects.PublishSubject;
import nu.marginalia.wmsa.smhi.model.Plats;
import nu.marginalia.wmsa.smhi.model.PrognosData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Singleton
public class SmhiEntityStore {
    private final ReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Map<Plats, PrognosData> data = new HashMap<>();

    public final PublishSubject<Plats> platser = PublishSubject.create();
    public final PublishSubject<PrognosData> prognosdata = PublishSubject.create();
    Logger logger = LoggerFactory.getLogger(getClass());
    public boolean offer(PrognosData modell) {
        Lock lock = this.rwl.writeLock();
        try {
            lock.lock();
            if (data.put(modell.plats, modell) == null) {
                platser.onNext(modell.plats);
            }
            prognosdata.onNext(modell);
        }
        finally {
            lock.unlock();
        }
        return true;
    }

    public List<Plats> platser() {
        Lock lock = this.rwl.readLock();
        try {
            lock.lock();
            return new ArrayList<>(data.keySet());
        }
        finally {
            lock.unlock();
        }
    }

    public PrognosData prognos(Plats plats) {
        Lock lock = this.rwl.readLock();
        try {
            lock.lock();
            return data.get(plats);
        }
        finally {
            lock.unlock();
        }
    }
}
