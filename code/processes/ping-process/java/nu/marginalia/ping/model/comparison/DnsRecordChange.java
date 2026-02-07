package nu.marginalia.ping.model.comparison;

import nu.marginalia.ping.model.DnsRecordsReference;
import nu.marginalia.ping.model.DomainDnsRecord;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public sealed interface DnsRecordChange {
    record None() implements DnsRecordChange { }
    record Changed(
            boolean aRecordsChanged,
            boolean aaaaRecordsChanged,
            boolean cnameRecordChanged,
            boolean mxRecordsChanged,
            boolean caaRecordsChanged,
            boolean txtRecordsChanged,
            boolean nsRecordsChanged,
            boolean soaRecordChanged
    ) implements DnsRecordChange {}

    static DnsRecordChange between(DomainDnsRecord before, DomainDnsRecord after) {

        boolean aaaaRecordsChanged = !compareRecords(before.aaaaRecords(), after.aaaaRecords());
        boolean aRecordsChanged = !compareRecords(before.aRecords(), after.aRecords());
        boolean cnameRecordChanged = !Objects.equals(before.cnameRecord(), after.cnameRecord());
        boolean mxRecordsChanged = !compareRecords(before.mxRecords(), after.mxRecords());
        boolean caaRecordsChanged = !compareRecords(before.caaRecords(), after.caaRecords());
        boolean txtRecordsChanged = !compareRecords(before.txtRecords(), after.txtRecords());
        boolean nsRecordsChanged = !compareRecords(before.nsRecords(), after.nsRecords());
        boolean soaRecordChanged = !Objects.equals(before.soaRecord(), after.soaRecord());

        boolean anyChanged = aaaaRecordsChanged ||
                aRecordsChanged ||
                cnameRecordChanged ||
                mxRecordsChanged ||
                caaRecordsChanged ||
                txtRecordsChanged ||
                nsRecordsChanged ||
                soaRecordChanged;
        if (!anyChanged) {
            return new DnsRecordChange.None();
        } else {
            return new DnsRecordChange.Changed(
                    aRecordsChanged,
                    aaaaRecordsChanged,
                    cnameRecordChanged,
                    mxRecordsChanged,
                    caaRecordsChanged,
                    txtRecordsChanged,
                    nsRecordsChanged,
                    soaRecordChanged
            );
        }

    }

    static boolean compareRecords(DnsRecordsReference beforeRecords, DnsRecordsReference afterRecords) {
        if (null == beforeRecords && null == afterRecords) {
            return true; // Both are null, no change
        }

        // empty and null are semantically equivalent
        if (null == beforeRecords)
            return afterRecords.isEmpty();
        if (null == afterRecords)
            return beforeRecords.isEmpty();

        return DnsRecordsReference.isEquivalent(beforeRecords, afterRecords); // Compare the sets for equality
    }
}
