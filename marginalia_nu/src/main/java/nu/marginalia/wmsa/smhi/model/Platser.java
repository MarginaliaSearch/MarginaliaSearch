package nu.marginalia.wmsa.smhi.model;


import java.util.List;

public class Platser {
    private final List<Plats> platser;

    public Platser(List<Plats> platser) {
        this.platser = platser;
    }

    public List<Plats> getPlatser() {
        return platser;
    }
}
