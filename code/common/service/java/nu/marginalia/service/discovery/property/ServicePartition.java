package nu.marginalia.service.discovery.property;

public sealed interface ServicePartition {
    String identifier();

    static Any any() { return new Any(); }
    static Multi multi() { return new Multi(); }
    static Partition partition(int node) { return new Partition(node); }
    static None none() { return new None(); }

    record Any() implements ServicePartition, PartitionTraits.Grpc, PartitionTraits.Unicast {
        public String identifier() { return "*"; }

    }
    record Multi() implements ServicePartition, PartitionTraits.Grpc, PartitionTraits.Multicast {
        public String identifier() { return "*"; }

    }
    record Partition(int node) implements ServicePartition, PartitionTraits.Grpc, PartitionTraits.Unicast {
        public String identifier() {
            return Integer.toString(node);
        }
    }
    record None() implements ServicePartition, PartitionTraits.NoGrpc {
        public String identifier() { return ""; }

    }
}
