package nu.marginalia.service.client;

public sealed interface NodeSelectionStrategy {
    boolean test(int node);
    record Any() implements NodeSelectionStrategy {
        @Override
        public boolean test(int node) {
            return true;
        }
    }
    record Just(int node) implements NodeSelectionStrategy {
        @Override
        public boolean test(int node) {
            return this.node == node;
        }
    }
}
