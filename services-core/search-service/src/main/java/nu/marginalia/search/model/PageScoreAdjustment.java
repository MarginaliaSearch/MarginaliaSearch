package nu.marginalia.search.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PageScoreAdjustment {
    final double titleAdj;
    final double titleFullHit;
    final double urlAdj;
    final double domainAdj;
    final double descAdj;
    final double descHitsAdj;

    private static final PageScoreAdjustment zero = new PageScoreAdjustment(0,0, 0,0,0, 0);
    public static PageScoreAdjustment zero() {
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
