package nu.marginalia.wmsa.renderer.request.smhi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import nu.marginalia.wmsa.smhi.model.Plats;

import java.util.List;

@NoArgsConstructor @AllArgsConstructor @Getter
public class RenderSmhiIndexReq {
    public List<Plats> platser;
}
