package nu.marginalia.api;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import jakarta.inject.Named;
import nu.marginalia.api.polar.PolarBenefit;
import nu.marginalia.api.polar.PolarBenefits;
import nu.marginalia.api.polar.PolarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class PolarModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(PolarModule.class);

    public void configure() {
        final String baseUri = System.getProperty("api.polar.baseUri");
        final String accessToken = System.getProperty("api.polar.accessToken");
        final String orgId = System.getProperty("api.polar.orgId");

        PolarClient polarClientInstance = new PolarClient(baseUri, accessToken, orgId);
        bind(PolarClient.class).toInstance(new PolarClient(baseUri, accessToken, orgId));

        if (polarClientInstance.isAvilable()) {
            Map<String, PolarBenefit> benefitsMap = new HashMap<>();

            addBenefit(benefitsMap, "api.polar.tierNcId",
                    new PolarBenefit("CC-BY-NC-SA 4.0", 1_000, 15, false));
            addBenefit(benefitsMap, "api.polar.tierMeteredId",
                    new PolarBenefit("UNRESTRICTED", 25, 15, true));
            addBenefit(benefitsMap, "api.polar.tier1Id",
                    new PolarBenefit("UNRESTRICTED", 2_500, 15, true));
            addBenefit(benefitsMap, "api.polar.tier2Id",
                    new PolarBenefit("UNRESTRICTED", 10_000, 30, true));
            addBenefit(benefitsMap, "api.polar.tier3Id",
                    new PolarBenefit("UNRESTRICTED", 100_000, 60, true));

            bind(PolarBenefits.class).toInstance(new PolarBenefits(benefitsMap));
        }
        else {
            bind(PolarBenefits.class).toInstance(PolarBenefits.asDisabled());
        }

    }

    public void addBenefit(Map<String, PolarBenefit> map, String property, PolarBenefit benefit) {
        var id = System.getProperty(property);
        if (id != null) {
            map.put(id, benefit);
        }
        else {
            throw new IllegalStateException("Missing polar benefit " + property);
        }
    }

}
