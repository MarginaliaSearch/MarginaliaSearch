package nu.marginalia.nsfw;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Named;

import java.util.List;

public class NsfwFilterModule extends AbstractModule {

    @Provides
    @Named("nsfw.dangerLists")
    public List<String> nsfwDomainLists1() {
        return List.of(
                "https://raw.githubusercontent.com/olbat/ut1-blacklists/refs/heads/master/blacklists/cryptojacking/domains",
                "https://raw.githubusercontent.com/olbat/ut1-blacklists/refs/heads/master/blacklists/malware/domains",
                "https://raw.githubusercontent.com/olbat/ut1-blacklists/refs/heads/master/blacklists/phishing/domains"
        );
    }
    @Provides
    @Named("nsfw.smutLists")
    public List<String> nsfwDomainLists2() {
        return List.of(
                "https://github.com/olbat/ut1-blacklists/raw/refs/heads/master/blacklists/adult/domains.gz",
                "https://raw.githubusercontent.com/olbat/ut1-blacklists/refs/heads/master/blacklists/gambling/domains"
        );
    }

    public void configure() {
    }
}
