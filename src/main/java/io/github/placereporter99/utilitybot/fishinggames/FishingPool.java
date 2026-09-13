package io.github.placereporter99.utilitybot.fishinggames;

import java.util.*;
import java.security.SecureRandom;
import java.util.function.Supplier;

public class FishingPool {
    private final Set<WeightedItem<String>> fishingList;
    @SafeVarargs
    public FishingPool(WeightedItem<String>... items) {
        fishingList = new HashSet<>(List.of(items));
    }

    public void addItem(String item, long weight) {
        fishingList.add(new WeightedItem<String>(item, weight));
    }

    public FishingInventory createLinkedInventory(Supplier<Void> listener) {
        return new FishingInventory(this, listener);
    }

    public FishingInventory loadLinkedInventory(Supplier<Void> listener, Map<String, String> inventory) {
        var r = new FishingInventory(this, listener);
        r.addSerializableInventory(inventory);
        return r;
    }

    public long[] getWeights() {
        return fishingList.stream().mapToLong(WeightedItem::weight).toArray();
    }

    public String[] getItems() {
        return (String[]) fishingList.stream().map(WeightedItem::item).toArray();
    }

    public String fish() {
        var sumCounter = 0L;
        var indexCounter = 0;
        var max = Arrays.stream(getWeights()).sum();
        var random = new SecureRandom().nextLong(1, max + 1);
        for (long i : getWeights()) {
            sumCounter += i;
            if (sumCounter >= random) {
                break;
            }
            indexCounter++;
        }
        return getItems()[indexCounter];
    }
}
