# Leaky Bucket Rate Limiter

A functional implementation of the leaky bucket algorithm for rate limiting in Java using Maven.

## Overview

This implementation provides a thread-safe, functional approach to rate limiting where each user has their own bucket with configurable capacity and leak rate. The system rejects requests that would overflow the bucket while allowing requests to pass through as the bucket naturally "leaks" over time.

## Core Components

### POJO Classes

1. **`BucketInfo`** - Immutable representation of a bucket's state
   - Current level (amount of "liquid" in bucket)
   - Last update timestamp
   - Methods for leaking and adding requests

2. **`RateLimiter`** - Immutable rate limiter configuration and state
   - Bucket capacity and leak rate
   - Map of all user buckets
   - Methods for bucket management

3. **`RequestResult`** - Immutable result of rate limit decisions
   - Boolean decision (allowed/rejected)
   - New rate limiter state after processing

### Service Layer

- **`LeakyBucketRateLimiterService`** - Core business logic implementing the required interface

## Design Decisions and Trade-offs

### 1. Functional/Immutable Approach

**Decision**: All data structures are immutable, operations return new instances rather than modifying existing ones.

**Benefits**:
- Thread-safe by design (no shared mutable state)
- Predictable behavior - no side effects
- Easy to reason about and test
- Supports concurrent access patterns
- History preservation possible (though not implemented)

**Trade-offs**:
- Higher memory usage due to object creation
- Potential performance impact from constant allocation
- More complex for developers used to mutable approaches
- Garbage collection pressure with high request volumes

**Mitigation**: For production use, consider implementing object pooling or using persistent data structures for better performance.

### 2. Time-Based Leaking Strategy

**Decision**: Buckets leak based on elapsed time since last update rather than continuous background processing.

**Benefits**:
- No background threads or timers needed
- Scales to unlimited users without resource overhead
- Precise leaking calculation based on actual time elapsed
- Stateless approach - no cleanup needed for inactive users

**Trade-offs**:
- Calculation required on every request
- Potentially complex time arithmetic
- Must handle edge cases like backwards time

**Alternative Considered**: Background thread approach was rejected due to complexity and resource overhead.

### 3. Per-User Bucket Isolation

**Decision**: Each user gets completely independent bucket state stored in a map.

**Benefits**:
- Perfect isolation between users
- Simple to understand and implement
- Easy to debug individual user behavior
- Flexible per-user configuration possible

**Trade-offs**:
- Memory usage grows with number of users
- No automatic cleanup of inactive users
- Map lookup overhead on every request

**Mitigation**: In production, implement TTL-based cleanup for inactive users.

### 4. Edge Case Handling Strategy

**Decision**: Graceful degradation rather than strict enforcement for edge cases.

**Backwards Time**: Update timestamp but don't apply negative leaking
- **Benefit**: System remains functional despite clock issues
- **Trade-off**: Slight deviation from pure algorithm

**Large Time Gaps**: Allow complete bucket drainage
- **Benefit**: System recovers from long periods of inactivity
- **Trade-off**: Potential for burst after long gaps

**New Users**: Start with empty buckets at current timestamp
- **Benefit**: Immediate service for new users
- **Trade-off**: Cold start advantage vs. existing users

### 5. Precision and Floating Point Usage

**Decision**: Use `double` for all time and level calculations.

**Benefits**:
- High precision for fractional leak rates
- Supports sub-second timing
- Natural arithmetic operations

**Trade-offs**:
- Potential floating-point precision errors in long-running systems
- Slightly higher memory usage vs. integers
- More complex equality comparisons in tests

### 6. Error Handling Philosophy

**Decision**: Fail fast with `IllegalArgumentException` for invalid inputs, graceful handling for business logic edge cases.

**Benefits**:
- Clear contract enforcement
- Easy debugging of integration issues
- Predictable behavior

**Trade-offs**:
- Requires careful exception handling by callers
- Less forgiving than lenient approaches

### 7. API Design Choices

**Decision**: Service class with static-like methods operating on immutable state objects.

**Benefits**:
- Clear separation of state and behavior
- Testable without complex setup
- Functional programming style
- Easy to compose and extend

**Trade-offs**:
- More verbose than object-oriented approach
- Requires discipline to maintain immutability
- Learning curve for developers expecting OOP patterns

### 8. Memory vs. CPU Trade-offs

**Choice Made**: Prioritize correctness and thread-safety over raw performance.

**Memory Impact**:
- New objects created for every operation
- Historical state preservation capability (unused but available)
- Map storage for all user buckets

**CPU Impact**:
- Time calculations on every request
- Object allocation/GC overhead
- Map lookups and copies

**Rationale**: For most rate limiting scenarios, correctness and maintainability outweigh performance concerns. The bottleneck is typically I/O or business logic, not the rate limiter itself.

### 9. Testing Strategy

**Decision**: Comprehensive unit tests covering all edge cases and normal operations.

**Coverage Includes**:
- Basic functionality verification
- Edge cases (backwards time, overflows, new users)
- Multiple user isolation
- Various leak rates and time scenarios
- Error conditions and parameter validation

## Performance Characteristics

- **Time Complexity**: O(1) for request processing, O(1) for bucket lookup
- **Space Complexity**: O(U) where U is the number of active users
- **Concurrency**: Fully thread-safe due to immutability
- **Scalability**: Linear memory growth with user count

## Production Considerations

For production deployment, consider:

1. **User Cleanup**: Implement TTL-based removal of inactive users
2. **Monitoring**: Add metrics for rejection rates, bucket levels, user counts
3. **Persistence**: Consider backing bucket state with external storage
4. **Performance**: Profile under load and consider optimizations if needed
5. **Configuration**: Make capacity and leak rates configurable per user or globally

## Alternative Approaches Considered

1. **Token Bucket**: More complex to implement correctly, similar performance characteristics
2. **Fixed Window**: Simpler but less smooth rate limiting behavior  
3. **Sliding Window**: More accurate but significantly higher memory usage
4. **Distributed Rate Limiting**: Requires external coordination (Redis, etc.)

The leaky bucket approach provides the best balance of accuracy, simplicity, and resource usage for most single-node applications.
