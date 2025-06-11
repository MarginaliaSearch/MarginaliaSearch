package nu.marginalia.ping.svc;

import com.google.inject.Inject;
import nu.marginalia.ping.PingIntervalsConfiguration;
import nu.marginalia.ping.model.DomainDnsRecord;
import nu.marginalia.ping.model.SingleDnsRecord;
import nu.marginalia.process.ProcessConfiguration;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class DomainDnsInformationFactory {

    private final Duration defaultDnsUpdateInterval;
    private final int nodeId;

    @Inject
    public DomainDnsInformationFactory(ProcessConfiguration processConfiguration,
                                       PingIntervalsConfiguration pingIntervalsConfiguration) {
        this.nodeId = processConfiguration.node();
        this.defaultDnsUpdateInterval = pingIntervalsConfiguration.dnsUpdateInterval();
    }

    public DomainDnsRecord create(String rootDomain,
                                  @Nullable Integer rootDomainId,
                                  List<SingleDnsRecord> records)
    {
        var builder = DomainDnsRecord.builder()
                .rootDomainName(rootDomain)
                .dnsRootDomainId(rootDomainId)
                .tsLastUpdate(Instant.now())
                .nodeAffinity(nodeId)
                .tsNextScheduledUpdate(Instant.now().plus(defaultDnsUpdateInterval));

        for (var record : records) {
            switch (record.recordType().toLowerCase()) {
                case "a":
                    builder.addARecord(record.data());
                    break;
                case "aaaa":
                    builder.addAaaaRecord(record.data());
                    break;
                case "cname":
                    builder.cnameRecord(record.data());
                    break;
                case "mx":
                    builder.addMxRecord(record.data());
                    break;
                case "caa":
                    builder.addCaaRecord(record.data());
                    break;
                case "txt":
                    builder.addTxtRecord(record.data());
                    break;
                case "ns":
                    builder.addNsRecord(record.data());
                    break;
                case "soa":
                    builder.soaRecord(record.data());
                    break;
                default:
                    // Ignore unknown record types
            }
        }

        return builder.build();
    }

}
