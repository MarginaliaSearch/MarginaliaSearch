package nu.marginalia.index.searchset.connectivity;

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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

    public boolean isEmpty() {
        return connectivity.isEmpty();
    }

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

    public Int2IntOpenHashMap emulateRankData() {
        Int2IntOpenHashMap ret = new Int2IntOpenHashMap(connectivity.size());
        connectivity.forEach((k,v) -> {
            ret.put((int) k, values[v].rankValue);
        });
        return ret;
    }
}
