package com.leakybucket.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * Immutable result of a rate limit check.
 * Contains the decision and the new rate limiter state.
 */
@Getter
@EqualsAndHashCode
public final class RateLimiterResponse {
    private final boolean allowed;
    private final RateLimiterCO newLimiterState;

    public RateLimiterResponse(boolean allowed, RateLimiterCO newLimiterState) {
        this.allowed = allowed;
        this.newLimiterState = Objects.requireNonNull(newLimiterState, "New limiter state cannot be null");
    }


    @Override
    public String toString() {
        return String.format("RequestResult{allowed=%s, limiter=%s}", allowed, newLimiterState);
    }
}