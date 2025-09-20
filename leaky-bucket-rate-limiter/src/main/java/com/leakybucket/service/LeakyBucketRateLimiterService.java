package com.leakybucket.service;


import com.leakybucket.model.BucketInfoCO;
import com.leakybucket.model.RateLimiterCO;
import com.leakybucket.model.RateLimiterResponse;

/**
 * Service implementing the leaky bucket rate limiter algorithm.
 * All operations are functional and return new immutable states.
 */
public class LeakyBucketRateLimiterService {

    /**
     * Creates a new rate limiter with specified capacity and leak rate.
     *
     * @param capacity Maximum bucket size
     * @param leakRate Units leaked per second
     * @return New RateLimiter instance
     * @throws IllegalArgumentException if capacity or leakRate are not positive
     */
    public RateLimiterCO createRateLimiter(double capacity, double leakRate) {
        return new RateLimiterCO(capacity, leakRate);
    }

    /**
     * Determines if a request should be allowed and returns the new state.
     *
     * @param limiter Current rate limiter state
     * @param userId User making the request
     * @param timestamp Current timestamp (Unix epoch seconds)
     * @return RequestResult containing decision and new limiter state
     * @throws IllegalArgumentException if any parameter is null
     */
    public RateLimiterResponse allowRequest(RateLimiterCO limiter, String userId, double timestamp) {
        if (limiter == null) {
            throw new IllegalArgumentException("Rate limiter cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        // Get current bucket state after applying leaking
        BucketInfoCO currentBucket = limiter.getLeakedBucketInfo(userId, timestamp);

        // Try to add the request
        BucketInfoCO newBucket = currentBucket.addRequest(limiter.getCapacity());

        if (newBucket == null) {
            // Request would overflow - reject but still update timestamp
            BucketInfoCO updatedBucket = new BucketInfoCO(currentBucket.getCurrentLevel(), timestamp);
            RateLimiterCO newLimiter = limiter.withUpdatedBucket(userId, updatedBucket);
            return new RateLimiterResponse(false, newLimiter);
        } else {
            // Request allowed - update bucket with new level
            RateLimiterCO newLimiter = limiter.withUpdatedBucket(userId, newBucket);
            return new RateLimiterResponse(true, newLimiter);
        }
    }

    /**
     * Gets the current bucket state for a user for debugging purposes.
     *
     * @param limiter Current rate limiter state
     * @param userId User ID to check
     * @return BucketInfo for the user, or null if user doesn't exist
     * @throws IllegalArgumentException if limiter or userId is null
     */
    public BucketInfoCO getBucketState(RateLimiterCO limiter, String userId) {
        if (limiter == null) {
            throw new IllegalArgumentException("Rate limiter cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return limiter.getBucketState(userId);
    }

    /**
     * Gets the current bucket state after applying leaking based on current time.
     * This is useful for debugging to see what the bucket level would be at a given time.
     *
     * @param limiter Current rate limiter state
     * @param userId User ID to check
     * @param timestamp Current timestamp
     * @return BucketInfo after leaking, or new empty bucket if user doesn't exist
     * @throws IllegalArgumentException if limiter or userId is null
     */
    public BucketInfoCO getBucketStateAtTime(RateLimiterCO limiter, String userId, double timestamp) {
        if (limiter == null) {
            throw new IllegalArgumentException("Rate limiter cannot be null");
        }
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }

        return limiter.getLeakedBucketInfo(userId, timestamp);
    }
}