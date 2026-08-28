package nu.marginalia.index.model;

import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import nu.marginalia.api.searchquery.model.compiled.CompiledQueryLong;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongToIntFunction;

public record TermSlotGroup(List<LongList> slots) {

    public static List<TermSlotGroup> fromPaths(CompiledQueryLong query, int[] variantClasses) {
        // Paths with the same set of variant classes belong to the same group
        Map<IntSet, Int2ObjectMap<LongList>> groups = new LinkedHashMap<>();

        for (IntList path : query.paths) {
            IntSet signature = new IntOpenHashSet(path.size());
            for (int idx : path) {
                signature.add(variantClasses[idx]);
            }

            var slots = groups.computeIfAbsent(signature, k -> new Int2ObjectAVLTreeMap<>());
            for (int idx : path) {
                LongList slot = slots.computeIfAbsent(variantClasses[idx], k -> new LongArrayList());
                long termId = query.at(idx);
                if (!slot.contains(termId)) {
                    slot.add(termId);
                }
            }
        }

        List<TermSlotGroup> ret = new ArrayList<>(groups.size());
        for (var slots : groups.values()) {
            ret.add(new TermSlotGroup(List.copyOf(slots.values())));
        }
        return ret;
    }

    /** A head term whose documents are the candidates, and the slots that filter them */
    public record Plan(long head, List<LongList> filters) {}

    public List<Plan> plan(LongToIntFunction hits, int documentCount) {
        List<LongList> byCost = new ArrayList<>(slots);
        byCost.sort(Comparator.comparingLong(slot -> cost(slot, hits)));

        LongList headSlot = byCost.getFirst();
        List<LongList> filterSlots = byCost.subList(1, byCost.size());

        List<Plan> plans = new ArrayList<>();
        List<LongList> commonFilters = new ArrayList<>(filterSlots.size());
        boolean commonPlanViable = true;

        double candidates = cost(headSlot, hits);

        for (int i = 0; i < filterSlots.size(); i++) {
            LongList slot = filterSlots.get(i);
            LongList common = new LongArrayList(slot.size());

            for (long term : slot) {
                if (slot.size() > 1 && hits.applyAsInt(term) < candidates) {
                    List<LongList> filters = new ArrayList<>(filterSlots.size());
                    filters.add(headSlot);
                    for (int j = 0; j < filterSlots.size(); j++) {
                        if (j != i) filters.add(filterSlots.get(j));
                    }
                    plans.add(new Plan(term, filters));
                }
                else {
                    common.add(term);
                }
            }

            if (common.isEmpty()) {
                commonPlanViable = false;
            }
            else {
                commonFilters.add(common);
            }

            candidates *= Math.min(1., (double) cost(slot, hits) / documentCount);
        }

        if (commonPlanViable) {
            for (long head : headSlot) {
                plans.add(new Plan(head, commonFilters));
            }
        }

        return plans;
    }

    private static long cost(LongList slot, LongToIntFunction hits) {
        long sum = 0;
        for (long termId : slot) {
            sum += hits.applyAsInt(termId);
        }
        return sum;
    }
}
