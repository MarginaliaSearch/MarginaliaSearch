package nu.marginalia.index.searchset.connectivity;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import org.jetbrains.annotations.Nullable;

public class ConnectivityView {
    @Nullable
    private final Int2ByteOpenHashMap connectivity;
    private final DomainSetConnectivity[] values = DomainSetConnectivity.values();
    private static final ConnectivityView EMPTY = new ConnectivityView();
    public ConnectivityView(Int2ByteOpenHashMap connectivity) {
        this.connectivity = connectivity;
    }
    private ConnectivityView() { this.connectivity = null; }

    public static ConnectivityView empty() {
        return EMPTY;
    }

    public DomainSetConnectivity get(int id) {
        if (connectivity == null || connectivity.isEmpty()) {
            return DomainSetConnectivity.UNKNOWN;
        }

        int ord = connectivity.getOrDefault(id, (byte) DomainSetConnectivity.UNREACHABLE.ordinal());
        return values[ord];
    }
}
