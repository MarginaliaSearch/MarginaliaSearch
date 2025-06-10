package nu.marginalia.ping.model;

public sealed interface HistoricalPingData {
    record JustAvailability(String domain, DomainAvailabilityRecord record) implements HistoricalPingData {}
    record AvailabilityAndSecurity(String domain, DomainAvailabilityRecord availabilityRecord, DomainSecurityRecord securityRecord) implements HistoricalPingData {}
}
