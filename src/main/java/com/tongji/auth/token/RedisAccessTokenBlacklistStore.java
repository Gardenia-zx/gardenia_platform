package com.tongji.auth.token;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class RedisAccessTokenBlacklistStore implements AccessTokenBlacklistStore{
    private final StringRedisTemplate stringRedisTemplate;
    public RedisAccessTokenBlacklistStore(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }
    @Override
    public void blacklist(String tokenId, Duration ttl) {
        if (!StringUtils.hasText(tokenId) || ttl == null || !ttl.isPositive()) {
            return;
        }
        String key  = buildKey(tokenId);
        stringRedisTemplate.opsForValue().set(key,"1",ttl);
    }

    @Override
    public boolean isBlacklisted(String tokenId) {
        if (!StringUtils.hasText(tokenId)) {
            return false;
        }
        String key = buildKey(tokenId);
        return StringUtils.hasText(stringRedisTemplate.opsForValue().get(key));
    }

    public String buildKey(String tokenId){
        return "auth:at:black:"+tokenId;
    }
}
