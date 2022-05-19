package nu.marginalia.wmsa.smhi.model;

import lombok.Getter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Getter
public class Plats {
    public final String namn;
    public final double latitud;
    public final double longitud;

    public String getUrl() {
        return namn.toLowerCase()+".html";
    }

    public Plats(String namn, String latitud, String longitud) {
        this.namn = namn;
        this.longitud = Double.parseDouble(longitud);
        this.latitud = Double.parseDouble(latitud);
    }

    public String toString() {
        return String.format("Plats[%s %s %s]", namn, longitud, latitud);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Plats plats = (Plats) o;

        return new EqualsBuilder().append(latitud, plats.latitud).append(longitud, plats.longitud).append(namn, plats.namn).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(namn).append(latitud).append(longitud).toHashCode();
    }
}
