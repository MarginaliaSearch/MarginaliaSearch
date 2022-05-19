package nu.marginalia.wmsa.renderer.request.smhi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nu.marginalia.wmsa.smhi.model.PrognosData;

@NoArgsConstructor @AllArgsConstructor @Getter
public class RenderSmhiPrognosReq {
    public PrognosData data;
}
