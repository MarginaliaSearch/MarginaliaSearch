package nu.marginalia.api.polar;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

@Singleton
public class PolarBenefits {
    private static final Logger logger = LoggerFactory.getLogger(PolarBenefit.class);

    public final Map<String, PolarBenefit> benefitsMap = new HashMap<>();

    @Inject
    public PolarBenefits(Map<String, PolarBenefit> benefitsMap) {
        this.benefitsMap.putAll(benefitsMap);
    }

    private PolarBenefits() {}


    public static PolarBenefits asDisabled() {
        return new PolarBenefits();
    }

    public Optional<PolarBenefit> getBenefit(String benefitId) {
        return Optional.ofNullable(benefitsMap.get(benefitId));
    }

    public Optional<PolarBenefit> getBenefit(PolarLicenseKey key) {
        return Optional.ofNullable(benefitsMap.get(key.benefitId()));
    }
}
