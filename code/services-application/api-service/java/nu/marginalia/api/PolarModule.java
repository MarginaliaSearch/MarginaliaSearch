package nu.marginalia.api;

import com.google.inject.AbstractModule;
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
        bind(PolarClient.class).toInstance(polarClientInstance);

        if (polarClientInstance.isAvilable()) {
            Map<String, PolarBenefit> benefitsMap = new HashMap<>();

            addBenefit(benefitsMap, "api.polar.tierNcId",
                    PolarBenefit.builder("CC-BY-NC-SA 4.0")
                            .queryRate(1_000, 15)
                            .siteInfoRate(10_000, 30)
                            .allowQueryOveruse(false)
                            .allowSiteInfoOveruse(false)
                            .build());
            addBenefit(benefitsMap, "api.polar.tierMeteredId",
                    PolarBenefit.builder("UNRESTRICTED")
                            .queryRate(25, 15)
                            .siteInfoRate(10_000, 30)
                            .allowQueryOveruse(true)
                            .allowSiteInfoOveruse(false)
                            .build());
            addBenefit(benefitsMap, "api.polar.tier1Id",
                    PolarBenefit.builder("UNRESTRICTED")
                            .queryRate(2_500, 15)
                            .siteInfoRate(10_000, 30)
                            .allowQueryOveruse(true)
                            .allowSiteInfoOveruse(false)
                            .build());
            addBenefit(benefitsMap, "api.polar.tier2Id",
                    PolarBenefit.builder("UNRESTRICTED")
                            .queryRate(10_000, 30)
                            .siteInfoRate(50_000, 60)
                            .allowQueryOveruse(true)
                            .allowSiteInfoOveruse(false)
                            .build());
            addBenefit(benefitsMap, "api.polar.tier3Id",
                    PolarBenefit.builder("UNRESTRICTED")
                            .queryRate(100_000, 60)
                            .siteInfoRate(500_000, 120)
                            .allowQueryOveruse(true)
                            .allowSiteInfoOveruse(false)
                            .build());

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
