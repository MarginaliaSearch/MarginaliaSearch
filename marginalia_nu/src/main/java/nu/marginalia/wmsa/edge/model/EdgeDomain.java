package nu.marginalia.wmsa.edge.model;

import lombok.*;

import javax.annotation.Nonnull;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@AllArgsConstructor
@Getter @Setter @Builder
public class EdgeDomain implements WideHashable {

    private static final Predicate<String> ipPatternTest = Pattern.compile("[\\d]{1,3}\\.[\\d]{1,3}\\.[\\d]{1,3}\\.[\\d]{1,3}").asMatchPredicate();
    private static final Predicate<String> govListTest = Pattern.compile(".*\\.(ac|co|org|gov|edu|com)\\.[a-z]{2}").asMatchPredicate();

    @Nonnull
    public final String subDomain;
    @Nonnull
    public final String domain;

    @SneakyThrows
    public EdgeDomain(String host) {
        Objects.requireNonNull(host, "domain name must not be null");

        var dot = host.lastIndexOf('.');

        if (dot < 0 || ipPatternTest.test(host)) { // IPV6 >.>
            subDomain = "";
            domain = host;
        }
        else {
            int dot2 = host.substring(0, dot).lastIndexOf('.');
            if (dot2 < 0) {
                subDomain = "";
                domain = host;
            }
            else {
                if (govListTest.test(host))
                { // Capture .ac.jp, .co.uk
                    int dot3 = host.substring(0, dot2).lastIndexOf('.');
                    if (dot3 >= 0) {
                        dot2 = dot3;
                        subDomain = host.substring(0, dot2);
                        domain = host.substring(dot2 + 1);
                    }
                    else {
                        subDomain = "";
                        domain = host;
                    }
                }
                else {
                    subDomain = host.substring(0, dot2);
                    domain = host.substring(dot2 + 1);
                }
            }
        }
    }

    public EdgeUrl toRootUrl() {
        // Set default protocol to http, as most https websites redirect http->https, but few http websites redirect https->http
        return new EdgeUrl("http", this, null, "/");
    }

    public String toString() {
        return getAddress();
    }

    public String getAddress() {
        if (!subDomain.isEmpty()) {
            return subDomain + "." + domain;
        }
        return domain;
    }

    public String getDomainKey() {
        int cutPoint = domain.indexOf('.');
        if (cutPoint < 0) {
            return domain;
        }
        return domain.substring(0, cutPoint).toLowerCase();
    }
    public String getLongDomainKey() {
        StringBuilder ret = new StringBuilder();

        int cutPoint = domain.indexOf('.');
        if (cutPoint < 0) {
            ret.append(domain);
        }
        else {
            ret.append(domain, 0, cutPoint);
        }

        if (!"".equals(subDomain) && !"www".equals(subDomain)) {
            ret.append(":");
            ret.append(subDomain);
        }

        return ret.toString().toLowerCase();
    }

    @Override
    public long wideHash() {
        return ((long) Objects.hash(domain, subDomain) << 32) | toString().hashCode();
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof EdgeDomain)) return false;
        final EdgeDomain other = (EdgeDomain) o;
        if (!other.canEqual((Object) this)) return false;
        final String this$subDomain = this.getSubDomain();
        final String other$subDomain = other.getSubDomain();
        if (!this$subDomain.equalsIgnoreCase(other$subDomain)) return false;
        final String this$domain = this.getDomain();
        final String other$domain = other.getDomain();
        if (!this$domain.equalsIgnoreCase(other$domain)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EdgeDomain;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $subDomain = this.getSubDomain().toLowerCase();
        result = result * PRIME + $subDomain.hashCode();
        final Object $domain = this.getDomain().toLowerCase();
        result = result * PRIME + $domain.hashCode();
        return result;
    }
}
