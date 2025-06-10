package nu.marginalia.ping.fetcher;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import nu.marginalia.ping.model.SingleDnsRecord;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class PingDnsFetcher {
    private final ThreadLocal<ExtendedResolver> resolver;
    private static final ExecutorService digExecutor = Executors.newFixedThreadPool(100);

    private static final int[] RECORD_TYPES = {
            Type.A, Type.AAAA, Type.NS, Type.MX, Type.TXT,
            Type.SOA, Type.CNAME, Type.CAA, Type.SPF
    };

    @Inject
    public PingDnsFetcher(@Named("ping.nameservers")
                      List<String> nameservers) throws UnknownHostException
    {
        resolver = ThreadLocal.withInitial(() -> createResolver(nameservers));
    }

    private ExtendedResolver createResolver(List<String> nameservers) {
        try {
            ExtendedResolver r = new ExtendedResolver(
                    nameservers.toArray(new String[0])
            );
            r.setLoadBalance(true);
            r.setTimeout(Duration.ofSeconds(5));
            return r;
        }
        catch (UnknownHostException e) {
            throw new RuntimeException("Failed to create DNS resolver", e);
        }
    }

    private List<SingleDnsRecord> query(String domainName, int recordType) throws TextParseException {
        var resolver = this.resolver.get();
        var query = new Lookup(domainName, recordType);
        query.setResolver(resolver);

        var result = query.run();

        if (result == null || result.length == 0) {
            return List.of();
        }

        List<SingleDnsRecord> records = new ArrayList<>(result.length);

        for (var record : result) {
            if (record == null) continue;
            records.add(new SingleDnsRecord(
                    Type.string(recordType),
                    record.toString())
            );

        }

        return records;
    }

    public List<SingleDnsRecord> dig(String domainName) {
        List<Callable<List<SingleDnsRecord>>> tasks = new ArrayList<>(RECORD_TYPES.length);
        for (var recordType : RECORD_TYPES) {
            tasks.add(() -> query(domainName, recordType));
        }
        List<SingleDnsRecord> results = new ArrayList<>(RECORD_TYPES.length);
        try {
            List<Future<List<SingleDnsRecord>>> futures = digExecutor.invokeAll(tasks);
            for (Future<List<SingleDnsRecord>> future : futures) {
                try {
                    results.addAll(future.get(1, TimeUnit.MINUTES));
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Error fetching DNS records: " + e.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("DNS query interrupted: " + e.getMessage());
        }
        return results;
    }

}
