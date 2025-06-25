package nu.marginalia.actor.task;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import nu.marginalia.actor.prototype.RecordActorPrototype;
import nu.marginalia.actor.state.ActorStep;
import nu.marginalia.mq.MqMessageState;
import nu.marginalia.mq.persistence.MqPersistence;
import nu.marginalia.nsfw.NsfwDomainFilter;
import nu.marginalia.service.module.ServiceConfiguration;

@Singleton
public class UpdateNsfwFiltersActor extends RecordActorPrototype {
    private final ServiceConfiguration serviceConfiguration;
    private final NsfwDomainFilter nsfwDomainFilter;
    private final MqPersistence persistence;

    public record Initial(long respondMsgId) implements ActorStep {}
    public record Run(long respondMsgId) implements ActorStep {}

    @Override
    public ActorStep transition(ActorStep self) throws Exception {
        return switch(self) {
            case Initial(long respondMsgId) -> {
                if (serviceConfiguration.node() != 1) {
                    persistence.updateMessageState(respondMsgId, MqMessageState.ERR);
                    yield new Error("This actor can only run on node 1");
                }
                else {
                    yield new Run(respondMsgId);
                }
            }
            case Run(long respondMsgId) -> {
                nsfwDomainFilter.fetchLists();
                persistence.updateMessageState(respondMsgId, MqMessageState.OK);
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
                                  NsfwDomainFilter nsfwDomainFilter,
                                  MqPersistence persistence)
    {
        super(gson);
        this.serviceConfiguration = serviceConfiguration;
        this.nsfwDomainFilter = nsfwDomainFilter;
        this.persistence = persistence;
    }

}
