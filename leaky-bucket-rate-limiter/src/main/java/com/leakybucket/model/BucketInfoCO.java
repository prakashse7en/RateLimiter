package com.leakybucket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Immutable representation of a bucket's current state.
 * Contains the current level and the last update timestamp.
 */
@Getter
@EqualsAndHashCode
public final class BucketInfoCO {
    private final double currentLevel;
    private final double lastUpdateTime;

    public BucketInfoCO(double currentLevel, double lastUpdateTime) {
        this.currentLevel = Math.max(0.0, currentLevel); // Ensure non-negative
        this.lastUpdateTime = lastUpdateTime;
    }

    /**
     * Creates a new BucketInfo with updated level after leaking.
     * 
     * @param newTimestamp The current timestamp
     * @param leakRate The rate at which the bucket leaks (units per second)
     * @return New BucketInfo with leaked amount applied
     */
    public BucketInfoCO leak(double newTimestamp, double leakRate) {
        if (newTimestamp < lastUpdateTime) {
            // Handle backwards time - don't leak, just update timestamp
            return new BucketInfoCO(currentLevel, newTimestamp);
        }
        
        double timeDelta = newTimestamp - lastUpdateTime;
        double leakedAmount = timeDelta * leakRate;
        double newLevel = Math.max(0.0, currentLevel - leakedAmount);
        
        return new BucketInfoCO(newLevel, newTimestamp);
    }

    /**
     * Creates a new BucketInfo after adding a request.
     * 
     * @param capacity The maximum bucket capacity
     * @return New BucketInfo with one unit added, or null if would overflow
     */
    public BucketInfoCO addRequest(double capacity) {
        if (currentLevel + 1.0 > capacity) {
            return null; // Would overflow
        }
        return new BucketInfoCO(currentLevel + 1.0, lastUpdateTime);
    }


    @Override
    public String toString() {
        return String.format("BucketInfo{level=%.2f, lastUpdate=%.2f}", 
                           currentLevel, lastUpdateTime);
    }
}