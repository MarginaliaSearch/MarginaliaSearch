package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.domsample.DomSampleService;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.outbox.MqOutbox;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.mqapi.tasks.ExportTaskRequest;
import nu.marginalia.process.ProcessOutboxes;
import nu.marginalia.process.ProcessSpawnerService;
import nu.marginalia.storage.FileStorageService;
import nu.marginalia.storage.model.FileStorageId;
import nu.marginalia.storage.model.FileStorageState;
import nu.marginalia.storage.model.FileStorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Singleton
public class DomSampleActor extends RecordActorPrototype {
    private final DomSampleService domSampleService;

    public record Initial() implements ActorStep {}
    public record Run() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Initial() -> {
                domSampleService.start();
                if (!domSampleService.isRunning())
                    yield new Error("Failed to start DOM sample service");

                yield new Run();
            }
            case Run() -> {
                for (;;) {
                    if (!domSampleService.isRunning()) {
                        yield new End();
                    }
                    TimeUnit.SECONDS.sleep(15);
                }
            }
            case End() -> {
                if (domSampleService.isRunning())
                    domSampleService.stop();
                yield new End(); // will not run, terminal state
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Run DOM sample service";
    }

    @Inject
    public DomSampleActor(Gson gson, DomSampleService domSampleService, ActorProcessWatcher processWatcher)
    {
        super(gson);
        this.domSampleService = domSampleService;
    }

}
