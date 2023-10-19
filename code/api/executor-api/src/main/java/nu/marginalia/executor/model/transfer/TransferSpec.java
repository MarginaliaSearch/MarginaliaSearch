package nu.marginalia.executor.model.transfer;

import java.util.List;

public record TransferSpec(List<TransferItem> items) {
    public TransferSpec() {
        this(List.of());
    }

    public int size() {
        return items.size();
    }
}
