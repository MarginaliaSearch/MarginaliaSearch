package nu.marginalia.api.polar;

public record PolarBenefit(String license,
                           int rateDaily,
                           int ratePerMinMax,
                           boolean allowOveruse)
{

}
