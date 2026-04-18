package com.swiftpay.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String PREFIX = "payment:idempotency:";

    private final StringRedisTemplate redisTemplate;

    public boolean reserve(String transactionId) {
        Boolean written = redisTemplate.opsForValue()
                .setIfAbsent(PREFIX + transactionId, "PENDING", IDEMPOTENCY_TTL);
        return Boolean.TRUE.equals(written);
    }

    public void markCompleted(String transactionId, String status) {
        redisTemplate.opsForValue().set(PREFIX + transactionId, status, IDEMPOTENCY_TTL);
    }
}
