package com.leakybucket;


import com.leakybucket.model.BucketInfoCO;
import com.leakybucket.model.RateLimiterCO;
import com.leakybucket.model.RateLimiterResponse;
import com.leakybucket.service.LeakyBucketRateLimiterService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;


class LeakyBucketRateLimiterServiceTest {

    private LeakyBucketRateLimiterService service;
    private RateLimiterCO limiter;

    @BeforeEach
    void setUp() {
        service = new LeakyBucketRateLimiterService();
        // Default: capacity=5, leak_rate=1.0 (1 unit per second)
        limiter = service.createRateLimiter(5.0, 1.0);
    }

    @Test
    @DisplayName("Should create rate limiter with valid parameters")
    void testCreateRateLimiter() {
        RateLimiterCO limiter = service.createRateLimiter(10.0, 2.0);

        assertNotNull(limiter);
        assertEquals(10.0, limiter.getCapacity(), 0.001);
        assertEquals(2.0, limiter.getLeakRate(), 0.001);
    }

    @Test
    @DisplayName("Should reject invalid parameters for rate limiter creation")
    void testCreateRateLimiterInvalidParams() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createRateLimiter(0.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> service.createRateLimiter(-1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> service.createRateLimiter(5.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> service.createRateLimiter(5.0, -1.0));
    }

    @Test
    @DisplayName("Should allow first request from new user")
    void testFirstRequestFromNewUser() {
        RateLimiterResponse result = service.allowRequest(limiter, "user1", 0.0);

        assertTrue(result.isAllowed());
        assertNotNull(result.getNewLimiterState());

        BucketInfoCO bucket = service.getBucketState(result.getNewLimiterState(), "user1");
        assertNotNull(bucket);
        assertEquals(1.0, bucket.getCurrentLevel(), 0.001);
        assertEquals(0.0, bucket.getLastUpdateTime(), 0.001);
    }



    @Test
    @DisplayName("Should handle burst requests and reject overflow")
    void testBurstHandling() {
        RateLimiterCO currentLimiter = limiter;
        RateLimiterResponse result;

        // Fill the bucket to capacity (5 requests)
        for (int i = 0; i < 5; i++) {
            result = service.allowRequest(currentLimiter, "user1", 0.0);
            assertTrue(result.isAllowed(), "Request " + (i + 1) + " should be allowed");
            currentLimiter = result.getNewLimiterState();
        }

        // 6th request should be rejected (overflow)
        result = service.allowRequest(currentLimiter, "user1", 0.0);
        assertFalse(result.isAllowed());

        // Bucket should still be at capacity
        BucketInfoCO bucket = service.getBucketState(result.getNewLimiterState(), "user1");
        assertEquals(5.0, bucket.getCurrentLevel(), 0.001);
    }

    @Test
    @DisplayName("Should handle time-based leaking correctly")
    void testTimeBasedLeaking() {
        // Fill bucket with 3 requests
        RateLimiterCO currentLimiter = limiter;
        for (int i = 0; i < 3; i++) {
            RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 0.0);
            currentLimiter = result.getNewLimiterState();
        }

        // Wait 2 seconds - should leak 2 units
        RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 2.0);
        assertTrue(result.isAllowed()); // Should work because 3-2+1 = 2 <= 5

        BucketInfoCO bucket = service.getBucketState(result.getNewLimiterState(), "user1");
        assertEquals(2.0, bucket.getCurrentLevel(), 0.001);
    }

    @Test
    @DisplayName("Should handle complete bucket drain")
    void testCompleteBucketDrain() {
        // Add 3 requests
        RateLimiterCO currentLimiter = limiter;
        for (int i = 0; i < 3; i++) {
            RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 0.0);
            currentLimiter = result.getNewLimiterState();
        }

        // Wait 5 seconds - should completely drain (leak rate = 1.0/sec)
        RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 5.0);
        assertTrue(result.isAllowed());

        BucketInfoCO bucket = service.getBucketState(result.getNewLimiterState(), "user1");
        assertEquals(1.0, bucket.getCurrentLevel(), 0.001); // Drained to 0, then +1
    }

    @Test
    @DisplayName("Should handle multiple independent users")
    void testMultipleUsers() {
        RateLimiterResponse result1 = service.allowRequest(limiter, "user1", 0.0);
        RateLimiterResponse result2 = service.allowRequest(result1.getNewLimiterState(), "user2", 0.0);

        assertTrue(result1.isAllowed());
        assertTrue(result2.isAllowed());

        RateLimiterCO finalLimiter = result2.getNewLimiterState();
        BucketInfoCO bucket1 = service.getBucketState(finalLimiter, "user1");
        BucketInfoCO bucket2 = service.getBucketState(finalLimiter, "user2");

        assertEquals(1.0, bucket1.getCurrentLevel(), 0.001);
        assertEquals(1.0, bucket2.getCurrentLevel(), 0.001);

        // Users should be independent - fill user1's bucket
        RateLimiterCO currentLimiter = finalLimiter;
        for (int i = 0; i < 4; i++) { // 4 more to reach capacity
            RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 0.0);
            currentLimiter = result.getNewLimiterState();
        }

        // user1 should be at capacity, user2 should still work
        RateLimiterResponse user1Result = service.allowRequest(currentLimiter, "user1", 0.0);
        RateLimiterResponse user2Result = service.allowRequest(currentLimiter, "user2", 0.0);

        assertFalse(user1Result.isAllowed()); // user1 at capacity
        assertTrue(user2Result.isAllowed());  // user2 has room
    }

    @Test
    @DisplayName("Should handle backwards time gracefully")
    void testBackwardsTime() {
        // Add a request at time 5
        RateLimiterResponse result1 = service.allowRequest(limiter, "user1", 5.0);
        assertTrue(result1.isAllowed());

        // Try request at time 3 (backwards)
        RateLimiterResponse result2 = service.allowRequest(result1.getNewLimiterState(), "user1", 3.0);
        assertTrue(result2.isAllowed()); // Should still work, no leaking applied

        BucketInfoCO bucket = service.getBucketState(result2.getNewLimiterState(), "user1");
        assertEquals(2.0, bucket.getCurrentLevel(), 0.001); // No leaking, just +1
        assertEquals(3.0, bucket.getLastUpdateTime(), 0.001); // Time updated
    }

    @Test
    @DisplayName("Should handle very large time gaps")
    void testLargeTimeGaps() {
        // Add request at time 0
        RateLimiterResponse result1 = service.allowRequest(limiter, "user1", 0.0);

        // Jump far into future
        RateLimiterResponse result2 = service.allowRequest(result1.getNewLimiterState(), "user1", 1000.0);
        assertTrue(result2.isAllowed());

        // Bucket should be completely drained and then have 1 unit
        BucketInfoCO bucket = service.getBucketState(result2.getNewLimiterState(), "user1");
        assertEquals(1.0, bucket.getCurrentLevel(), 0.001);
        assertEquals(1000.0, bucket.getLastUpdateTime(), 0.001);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.5, 1.0, 2.0, 5.0})
    @DisplayName("Should work with different leak rates")
    void testDifferentLeakRates(double leakRate) {
        RateLimiterCO testLimiter = service.createRateLimiter(10.0, leakRate);

        // Add 5 requests
        RateLimiterCO currentLimiter = testLimiter;
        for (int i = 0; i < 5; i++) {
            RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 0.0);
            currentLimiter = result.getNewLimiterState();
        }

        // Wait 2 seconds and make another request
        RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 2.0);
        assertTrue(result.isAllowed());

        // Expected level: 5 - (2 * leakRate) + 1
        double expectedLevel = Math.max(0.0, 5.0 - (2.0 * leakRate)) + 1.0;
        BucketInfoCO bucket = service.getBucketState(result.getNewLimiterState(), "user1");
        assertEquals(expectedLevel, bucket.getCurrentLevel(), 0.001);
    }

    @Test
    @DisplayName("Should handle null parameters gracefully")
    void testNullParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> service.allowRequest(null, "user1", 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> service.allowRequest(limiter, null, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> service.getBucketState(null, "user1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.getBucketState(limiter, null));
    }

    @Test
    @DisplayName("Should return null for non-existent user bucket")
    void testNonExistentUser() {
        BucketInfoCO bucket = service.getBucketState(limiter, "nonexistent");
        assertNull(bucket);
    }

    @Test
    @DisplayName("Should handle fractional leak rates and timestamps")
    void testFractionalValues() {
        RateLimiterCO testLimiter = service.createRateLimiter(5.0, 0.5); // 0.5 units per second

        // Add 2 requests
        RateLimiterResponse result1 = service.allowRequest(testLimiter, "user1", 0.0);
        RateLimiterResponse result2 = service.allowRequest(result1.getNewLimiterState(), "user1", 0.0);

        // Wait 1.5 seconds - should leak 0.75 units
        RateLimiterResponse result3 = service.allowRequest(result2.getNewLimiterState(), "user1", 1.5);
        assertTrue(result3.isAllowed());

        BucketInfoCO bucket = service.getBucketState(result3.getNewLimiterState(), "user1");
        // Expected: 2 - (1.5 * 0.5) + 1 = 2 - 0.75 + 1 = 2.25
        assertEquals(2.25, bucket.getCurrentLevel(), 0.001);
    }

    @Test
    @DisplayName("Should handle getBucketStateAtTime correctly")
    void testGetBucketStateAtTime() {
        // Add 3 requests at time 0
        RateLimiterCO currentLimiter = limiter;
        for (int i = 0; i < 3; i++) {
            RateLimiterResponse result = service.allowRequest(currentLimiter, "user1", 0.0);
            currentLimiter = result.getNewLimiterState();
        }

        // Check bucket state at time 2 without making a request
        BucketInfoCO bucketAtTime2 = service.getBucketStateAtTime(currentLimiter, "user1", 2.0);

        // Should show leaked state: 3 - (2 * 1.0) = 1.0
        assertEquals(1.0, bucketAtTime2.getCurrentLevel(), 0.001);
        assertEquals(2.0, bucketAtTime2.getLastUpdateTime(), 0.001);

        // Original limiter state should be unchanged
        BucketInfoCO originalBucket = service.getBucketState(currentLimiter, "user1");
        assertEquals(3.0, originalBucket.getCurrentLevel(), 0.001);
        assertEquals(0.0, originalBucket.getLastUpdateTime(), 0.001);
    }

    @Test
    @DisplayName("Should allow basic requests and demonstrate leaking behavior")
    void testBasicFunctionalityRequest() {
        // Basic functionality: capacity=5, leak_rate=1.0
        RateLimiterCO limiter = service.createRateLimiter(5.0, 1.0);

        // [allowed1, limiter] = allow_request(limiter, "user1", timestamp=0)
        RateLimiterResponse result1 = service.allowRequest(limiter, "user1", 0.0);
        assertTrue(result1.isAllowed(), "First request should be allowed");

        // [allowed2, limiter] = allow_request(limiter, "user1", timestamp=1)
        RateLimiterResponse result2 = service.allowRequest(result1.getNewLimiterState(), "user1", 1.0);
        assertTrue(result2.isAllowed(), "Second request should be allowed after 1 second");

        // Verify leaking: bucket had 1, leaked 1 in 1 second, added 1 = 1 total
        BucketInfoCO bucket = service.getBucketState(result2.getNewLimiterState(), "user1");
        assertEquals(1.0, bucket.getCurrentLevel(), 0.001);
    }

    @Test
    @DisplayName("Should reject requests when bucket reaches capacity (burst handling)")
    void testBurstHandlingRequest() {
        // Smaller capacity for easier testing
        RateLimiterCO currentLimiter = service.createRateLimiter(3.0, 1.0);

        // Fill to capacity
        for (int i = 0; i < 3; i++) {
            RateLimiterResponse result = service.allowRequest(currentLimiter, "burstUser", 0.0);
            assertTrue(result.isAllowed(), "Request " + (i+1) + " should fill bucket");
            currentLimiter = result.getNewLimiterState();
        }

        // Next requests should be rejected (burst)
        RateLimiterResponse burstResult1 = service.allowRequest(currentLimiter, "burstUser", 0.0);
        RateLimiterResponse burstResult2 = service.allowRequest(burstResult1.getNewLimiterState(), "burstUser", 0.0);

        assertFalse(burstResult1.isAllowed(), "Burst request 1 should be rejected");
        assertFalse(burstResult2.isAllowed(), "Burst request 2 should be rejected");
    }

    @Test
    @DisplayName("Should allow requests after sufficient time has passed for leaking")
    public void testTimeBasedLeakingRequest() {
        // 2 units per second leak rate
        RateLimiterCO leakState = service.createRateLimiter(5.0, 2.0);

        // Fill with 4 requests at time 0
        for (int i = 0; i < 4; i++) {
            RateLimiterResponse result = service.allowRequest(leakState, "leakUser", 0.0);
            leakState = result.getNewLimiterState();
        }

        // At time 2: bucket should leak 4 units (2 seconds * 2 units/sec = 4 leaked)
        // 4 - 4 = 0, so request should be allowed
        RateLimiterResponse afterLeak = service.allowRequest(leakState, "leakUser", 2.0);
        assertTrue(afterLeak.isAllowed(), "Should be allowed after leaking");

        BucketInfoCO leakedBucket = service.getBucketState(afterLeak.getNewLimiterState(), "leakUser");
        assertEquals(1.0, leakedBucket.getCurrentLevel(), 0.001); // 0 + 1 = 1
    }

    @Test
    @DisplayName("Should maintain independent buckets for different users")
   public void testMultipleUsersIndependence() {
        RateLimiterCO multiUserLimiter = service.createRateLimiter(2.0, 1.0); // Small capacity
        RateLimiterResponse currentState = new RateLimiterResponse(true, multiUserLimiter);

        // Fill user1's bucket to capacity
        RateLimiterResponse u1r1 = service.allowRequest(currentState.getNewLimiterState(), "user1", 0.0);
        RateLimiterResponse u1r2 = service.allowRequest(u1r1.getNewLimiterState(), "user1", 0.0);
        assertTrue(u1r1.isAllowed() && u1r2.isAllowed(), "User1 should fill bucket");

        // user1 should now be at capacity
        RateLimiterResponse u1r3 = service.allowRequest(u1r2.getNewLimiterState(), "user1", 0.0);
        assertFalse(u1r3.isAllowed(), "User1 should be at capacity");

        // user2 should be independent and have empty bucket
        RateLimiterResponse u2r1 = service.allowRequest(u1r3.getNewLimiterState(), "user2", 0.0);
        assertTrue(u2r1.isAllowed(), "User2 should have independent empty bucket");

        // Verify independence: user1 still at capacity, user2 has room
        BucketInfoCO user1Bucket = service.getBucketState(u2r1.getNewLimiterState(), "user1");
        BucketInfoCO user2Bucket = service.getBucketState(u2r1.getNewLimiterState(), "user2");

        assertEquals(2.0, user1Bucket.getCurrentLevel(), 0.001); // At capacity
        assertEquals(1.0, user2Bucket.getCurrentLevel(), 0.001); // Just added 1
    }

    @Test
    @DisplayName("Should verify bucket capacity enforcement at boundary conditions")
    void testCapacityBoundaryConditions() {
        RateLimiterCO limiter = service.createRateLimiter(3.0, 1.0);
        RateLimiterResponse currentState = new RateLimiterResponse(true, limiter);

        // Fill to exactly capacity
        for (int i = 0; i < 3; i++) {
            RateLimiterResponse result = service.allowRequest(currentState.getNewLimiterState(), "boundaryUser", 0.0);
            assertTrue(result.isAllowed(), "Request " + (i+1) + " should be allowed within capacity");
            currentState = result;
        }

        // Verify we're at capacity
        BucketInfoCO bucketAtCapacity = service.getBucketState(currentState.getNewLimiterState(), "boundaryUser");
        assertEquals(3.0, bucketAtCapacity.getCurrentLevel(), 0.001);

        // Next request should be rejected
        RateLimiterResponse overCapacity = service.allowRequest(currentState.getNewLimiterState(), "boundaryUser", 0.0);
        assertFalse(overCapacity.isAllowed(), "Request exceeding capacity should be rejected");
    }


}