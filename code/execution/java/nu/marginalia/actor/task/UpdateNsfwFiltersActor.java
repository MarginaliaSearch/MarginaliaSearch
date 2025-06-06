package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.nsfw.NsfwDomainFilter;
import nu.marginalia.service.module.ServiceConfiguration;

@Singleton
public class UpdateNsfwFiltersActor extends RecordActorPrototype {
    private final ServiceConfiguration serviceConfiguration;
    private final NsfwDomainFilter nsfwDomainFilter;

    public record Initial() implements ActorStep {}
    public record Run() implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Initial() -> {
                if (serviceConfiguration.node() != 1) {
                    yield new Error("This actor can only run on node 1");
                }
                else {
                    yield new Run();
                }
            }
            case Run() -> {
                nsfwDomainFilter.fetchLists();
                yield new End();
            }
            default -> new Error();
        };
    }

    @Override
    public String describe() {
        return "Sync NSFW filters";
    }

    @Inject
    public UpdateNsfwFiltersActor(Gson gson,
                                  ServiceConfiguration serviceConfiguration,
                                  NsfwDomainFilter nsfwDomainFilter)
    {
        super(gson);
        this.serviceConfiguration = serviceConfiguration;
        this.nsfwDomainFilter = nsfwDomainFilter;
    }

}
