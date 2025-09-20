package com.leakybucket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable representation of the rate limiter state.
 * Contains configuration and all user buckets.
 */
@Getter
@EqualsAndHashCode
public final class RateLimiterCO {

    private final double capacity;
    private final double leakRate;
    private final Map<String, BucketInfoCO> userBuckets;

    public RateLimiterCO(double capacity, double leakRate) {
        this(capacity, leakRate, Collections.emptyMap());
    }

    private RateLimiterCO(double capacity, double leakRate, Map<String, BucketInfoCO> userBuckets) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (leakRate <= 0) {
            throw new IllegalArgumentException("Leak rate must be positive");
        }
        
        this.capacity = capacity;
        this.leakRate = leakRate;
        this.userBuckets = Collections.unmodifiableMap(new HashMap<>(userBuckets));
    }

    /**
     * Gets the current bucket state for a user.
     * 
     * @param userId The user ID
     * @return BucketInfo for the user, or null if user doesn't exist
     */
    public BucketInfoCO getBucketState(String userId) {
        return userBuckets.get(userId);
    }

    /**
     * Creates a new RateLimiter with updated bucket state for a user.
     * 
     * @param userId The user ID
     * @param bucketInfo The new bucket info (null to remove user)
     * @return New RateLimiter instance
     */
    public RateLimiterCO withUpdatedBucket(String userId, BucketInfoCO bucketInfo) {
        Map<String, BucketInfoCO> newBuckets = new HashMap<>(userBuckets);
        
        if (bucketInfo == null) {
            newBuckets.remove(userId);
        } else {
            newBuckets.put(userId, bucketInfo);
        }
        
        return new RateLimiterCO(capacity, leakRate, newBuckets);
    }

    /**
     * Gets the current bucket info after applying leaking based on timestamp.
     * 
     * @param userId The user ID
     * @param timestamp Current timestamp
     * @return Leaked bucket info, or new bucket if user doesn't exist
     */
    public BucketInfoCO getLeakedBucketInfo(String userId, double timestamp) {
        BucketInfoCO currentBucket = userBuckets.get(userId);
        
        if (currentBucket == null) {
            // New user gets an empty bucket
            return new BucketInfoCO(0.0, timestamp);
        }
        
        return currentBucket.leak(timestamp, leakRate);
    }


    @Override
    public String toString() {
        return String.format("RateLimiter{capacity=%.1f, leakRate=%.1f, users=%d}", 
                           capacity, leakRate, userBuckets.size());
    }
}