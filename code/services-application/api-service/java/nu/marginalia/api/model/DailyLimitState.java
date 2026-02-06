package nu.marginalia.api.model;

public sealed interface DailyLimitState {

    record NoResults(int remaining) implements DailyLimitState {}
    record UnderLimit(int remaining) implements DailyLimitState {}
    record OverLimitCharge() implements DailyLimitState {
        public int remaining() { return 0; }
    }
    record OverLimitBlock() implements DailyLimitState {
        public int remaining() { return 0; }
    }
    record Cached(int remaining) implements DailyLimitState { }

    int remaining();

    default String name() {
        return getClass().getSimpleName();
    }
}
