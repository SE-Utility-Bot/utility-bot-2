package io.github.placereporter99.utilitybot.fishinggames;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.security.SecureRandom;

public class FishingInventory {
    private final FishingPool pool;
    private final Map<String, Long> inventory = new HashMap<String, Long>();
    private final Supplier<Void> listener;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> future = null;
    private long nextFishTime;
    private boolean rodCasted;
    public FishingInventory(FishingPool pool, Supplier<Void> listener) {
        this.pool = pool;
        this.listener = listener;
    }

    public void addInventory(Map<String, Long> inventory) {
        this.inventory.putAll(inventory);
    }

    public void addSerializableInventory(Map<String, String> inventory) {
        var intermediateMap = new HashMap<String, Long>();
        inventory.forEach((k, v) -> intermediateMap.put(k, Long.parseLong(v)));
        this.inventory.putAll(intermediateMap);
    }

    public boolean isRodCasted() {
        return rodCasted;
    }

    public Map<String, Long> getInventory() {
        return inventory;
    }

    public Map<String, String> getSerializableInventory() {
        return Map.ofEntries(inventory.entrySet().stream().map(x -> Map.entry(x.getKey(), x.getValue().toString())).toArray(Map.Entry[]::new));
    }

    public String pullRod() {
        if (rodCasted) {
            rodCasted = false;
            future.cancel(true);
            if (Instant.now().getEpochSecond() >= nextFishTime && Instant.now().getEpochSecond() <= nextFishTime + 1800) {
                var result = pool.fish();
                inventory.put(result, inventory.getOrDefault(result, 0L) + 1);
                return result;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public void throwRod() {
        if (!rodCasted) {
            var waitTime = new SecureRandom().nextLong(600, 1801);
            if (System.getenv().containsKey("TEST")) {
                waitTime = 30;
            }
            nextFishTime = Instant.now().getEpochSecond() + waitTime;
            future = scheduler.schedule(listener::get, waitTime, TimeUnit.SECONDS);
            rodCasted = true;
        }
    }

    public boolean dispose(String item) {
        if (contains(item)) {
            inventory.compute(item, (k, v) -> v - 1);
            return true;
        } else {
            return false;
        }
    }

    public boolean contains(String item) {
        return inventory.containsKey(item) && inventory.get(item) > 0;
    }
}
