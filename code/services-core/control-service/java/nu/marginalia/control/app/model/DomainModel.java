package nu.marginalia.control.app.model;

public record DomainModel(int id,
                          String name,
                          String ip,
                          int nodeAffinity,
                          double rank,
                          boolean blacklisted) {

    public boolean isUnassigned() {
        return nodeAffinity < 0;
    }

    public DomainAffinityState getAffinityState() {
        if (nodeAffinity < 0) {
            return DomainAffinityState.Known;
        }
        else if (nodeAffinity == 0) {
            return DomainAffinityState.Scheduled;
        }
        else {
            return DomainAffinityState.Assigned;
        }
    }

    public enum DomainAffinityState {
        Assigned("The domain has been assigned to a node."),
        Scheduled("The domain will be assigned to the next crawling node."),
        Known("The domain is known but not yet scheduled for crawling.");

        private final String desc;
        DomainAffinityState(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return desc;
        }
    }
}
