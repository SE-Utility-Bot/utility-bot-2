package io.github.placereporter99.utilitybot.fishinggames;

public record WeightedItem<T>(T item, long weight) {
    public WeightedItem {
        if (weight <= 0) {
            throw new IllegalArgumentException("Negative or zero weights not supported.");
        }
    }
}
