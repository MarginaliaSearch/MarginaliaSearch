package nu.marginalia.wmsa.edge.model.search;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EdgePageScoreAdjustment {
    final double titleAdj;
    final double titleFullHit;
    final double urlAdj;
    final double domainAdj;
    final double descAdj;
    final double descHitsAdj;

    private static final EdgePageScoreAdjustment zero = new EdgePageScoreAdjustment(0,0, 0,0,0, 0);
    public static EdgePageScoreAdjustment zero() {
        return zero;
    }

    public double getScore() {
        return titleAdj + titleFullHit + urlAdj + domainAdj + descAdj + descHitsAdj;
    }

    @Override
    public String toString() {
        return String.format("(%2.2f %2.2f %2.2f %2.2f %2.2f %2.2f)=%2.2f",
                titleAdj, titleFullHit, urlAdj, domainAdj, descAdj, descHitsAdj, getScore());
    }
}
