package nu.marginalia.converting.sideload.dirtree;

import java.util.List;

class DirtreeSideloadSpecList {
    public List<DirtreeSideloadSpec> sources;

    public DirtreeSideloadSpecList(List<DirtreeSideloadSpec> sources) {
        this.sources = sources;
    }

    public DirtreeSideloadSpecList() {
    }

    public List<DirtreeSideloadSpec> getSources() {
        return this.sources;
    }

    public void setSources(List<DirtreeSideloadSpec> sources) {
        this.sources = sources;
    }
}
