package com.tongji.auth.service;

import com.tongji.auth.config.AuthProperties;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * 登录风控服务：
 * - 账号维度
 * - IP 维度
 * - 账号+IP 组合维度
 */
@Service
@RequiredArgsConstructor
public class LoginRiskControlService {

    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties authProperties;

    public void assertLoginAllowed(String identifier, String ip) {
        if (isLocked(identifierLockKey(identifier))) {
            throw new BusinessException(ErrorCode.LOGIN_RESTRICTED, "账号登录受限，请稍后再试");
        }

        if (StringUtils.hasText(ip) && isLocked(ipLockKey(ip))) {
            throw new BusinessException(ErrorCode.LOGIN_RESTRICTED, "当前网络环境登录受限，请稍后再试");
        }

        if (StringUtils.hasText(ip) && isLocked(pairLockKey(identifier, ip))) {
            throw new BusinessException(ErrorCode.LOGIN_RESTRICTED, "当前登录尝试过于频繁，请稍后再试");
        }
    }

    public void recordFailure(String identifier, String ip) {
        AuthProperties.LoginRisk cfg = authProperties.getLoginRisk();

        incrementWithWindow(
                identifierFailKey(identifier),
                cfg.getFailureWindow(),
                cfg.getIdentifierFailureThreshold(),
                identifierLockKey(identifier),
                cfg.getLockDuration()
        );

        if (StringUtils.hasText(ip)) {
            incrementWithWindow(
                    ipFailKey(ip),
                    cfg.getFailureWindow(),
                    cfg.getIpFailureThreshold(),
                    ipLockKey(ip),
                    cfg.getLockDuration()
            );

            incrementWithWindow(
                    pairFailKey(identifier, ip),
                    cfg.getFailureWindow(),
                    cfg.getPairFailureThreshold(),
                    pairLockKey(identifier, ip),
                    cfg.getLockDuration()
            );
        }
    }

    public void clearOnSuccess(String identifier, String ip) {
        stringRedisTemplate.delete(identifierFailKey(identifier));
        stringRedisTemplate.delete(identifierLockKey(identifier));

        if (StringUtils.hasText(ip)) {
            stringRedisTemplate.delete(pairFailKey(identifier, ip));
            stringRedisTemplate.delete(pairLockKey(identifier, ip));
        }

        // IP 维度不主动清理，让它自然过期，保留一定风险画像
    }

    private void incrementWithWindow(
            String failKey,
            Duration failureWindow,
            int threshold,
            String lockKey,
            Duration lockDuration
    ) {
        Long count = stringRedisTemplate.opsForValue().increment(failKey);
        if (Objects.equals(count, 1L)) {
            stringRedisTemplate.expire(failKey, failureWindow);
        }

        if (count != null && count >= threshold) {
            stringRedisTemplate.opsForValue().set(lockKey, "1", lockDuration);
        }
    }

    private boolean isLocked(String key) {
        return StringUtils.hasText(stringRedisTemplate.opsForValue().get(key));
    }

    private String identifierFailKey(String identifier) {
        return "auth:login:fail:identifier:" + identifier;
    }

    private String identifierLockKey(String identifier) {
        return "auth:login:lock:identifier:" + identifier;
    }

    private String ipFailKey(String ip) {
        return "auth:login:fail:ip:" + ip;
    }

    private String ipLockKey(String ip) {
        return "auth:login:lock:ip:" + ip;
    }

    private String pairFailKey(String identifier, String ip) {
        return "auth:login:fail:pair:" + identifier + ":" + ip;
    }

    private String pairLockKey(String identifier, String ip) {
        return "auth:login:lock:pair:" + identifier + ":" + ip;
    }
}