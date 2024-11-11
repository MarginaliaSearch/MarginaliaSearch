package nu.marginalia.api.model;


import org.jetbrains.annotations.NotNull;

public class ApiLicense {
    /**
     * Key ID
     */
    @NotNull
    public String key;

    /**
     * License terms
     */
    @NotNull
    public String license;

    /**
     * License holder name
     */
    @NotNull
    public String name;

    /**
     * Requests per minute. If zero or less, unrestricted.
     */
    public int rate;

    public ApiLicense(@NotNull String key, @NotNull String license, @NotNull String name, int rate) {
        this.key = key;
        this.license = license;
        this.name = name;
        this.rate = rate;
    }

    public @NotNull String getKey() {
        return this.key;
    }

    public @NotNull String getLicense() {
        return this.license;
    }

    public @NotNull String getName() {
        return this.name;
    }

    public int getRate() {
        return this.rate;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ApiLicense)) return false;
        final ApiLicense other = (ApiLicense) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$key = this.getKey();
        final Object other$key = other.getKey();
        if (this$key == null ? other$key != null : !this$key.equals(other$key)) return false;
        final Object this$license = this.getLicense();
        final Object other$license = other.getLicense();
        if (this$license == null ? other$license != null : !this$license.equals(other$license)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        if (this.getRate() != other.getRate()) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ApiLicense;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $key = this.getKey();
        result = result * PRIME + ($key == null ? 43 : $key.hashCode());
        final Object $license = this.getLicense();
        result = result * PRIME + ($license == null ? 43 : $license.hashCode());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        result = result * PRIME + this.getRate();
        return result;
    }
}
