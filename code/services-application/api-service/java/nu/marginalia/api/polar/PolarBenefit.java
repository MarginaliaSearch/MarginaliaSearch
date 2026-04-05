package nu.marginalia.api.polar;

public record PolarBenefit(String license,
                           int rateDaily,
                           int ratePerMinMax,
                           boolean allowQueryOveruse,
                           int siteInfoRateDaily,
                           int siteInfoRatePerMinMax,
                           boolean allowSiteInfoOveruse)
{

    public static Builder builder(String license) {
        return new Builder(license);
    }

    public static class Builder {
        private final String license;
        private int queryDaily;
        private int queryPerMin;
        private boolean allowQueryOveruse;
        private int siteInfoDaily;
        private int siteInfoPerMin;
        private boolean allowSiteInfoOveruse;

        private Builder(String license) {
            this.license = license;
        }

        public Builder queryRate(int daily, int perMinute) {
            this.queryDaily = daily;
            this.queryPerMin = perMinute;
            return this;
        }

        public Builder siteInfoRate(int daily, int perMinute) {
            this.siteInfoDaily = daily;
            this.siteInfoPerMin = perMinute;
            return this;
        }

        public Builder allowQueryOveruse(boolean allowQueryOveruse) {
            this.allowQueryOveruse = allowQueryOveruse;
            return this;
        }

        public Builder allowSiteInfoOveruse(boolean allowSiteInfoOveruse) {
            this.allowSiteInfoOveruse = allowSiteInfoOveruse;
            return this;
        }

        public PolarBenefit build() {
            return new PolarBenefit(license, queryDaily, queryPerMin, allowQueryOveruse,
                    siteInfoDaily, siteInfoPerMin, allowSiteInfoOveruse);
        }
    }
}
