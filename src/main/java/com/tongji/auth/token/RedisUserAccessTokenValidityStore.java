package com.tongji.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class RedisUserAccessTokenValidityStore implements UserAccessTokenValidityStore {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisUserAccessTokenValidityStore(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void setValidAfter(long userId, Instant validAfter, Duration ttl) {
        if (validAfter == null || ttl == null || !ttl.isPositive()) {
            return;
        }
        stringRedisTemplate.opsForValue().set(buildKey(userId), validAfter.toString(), ttl);
    }

    @Override
    public Optional<Instant> getValidAfter(long userId) {
        String value = stringRedisTemplate.opsForValue().get(buildKey(userId));
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        return Optional.of(Instant.parse(value));
    }

    private String buildKey(long userId) {
        return "auth:user:valid-after:" + userId;
    }
}