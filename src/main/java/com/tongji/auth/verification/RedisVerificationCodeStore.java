package com.tongji.auth.verification;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Redis 的验证码存储实现。
 * <p>
 * 使用 Hash 结构保存 `code`、`maxAttempts` 与 `attempts`，TTL 控制有效期。
 * 校验时支持尝试计数与错误状态返回，成功后删除键以防重用。
 */
@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String FIELD_CODE = "code";
    private static final String FIELD_MAX_ATTEMPTS = "maxAttempts";
    private static final String FIELD_ATTEMPTS = "attempts";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

//    执行lua脚本的类
    private static final DefaultRedisScript<String> VERIFY_CODE_SCRIPT;

    static {
//        创建空的脚本对象
        VERIFY_CODE_SCRIPT = new DefaultRedisScript<>();
//        脚本存放的位置
        VERIFY_CODE_SCRIPT.setLocation(new ClassPathResource("scripts/auth/verify_code.lua"));
//        脚本返回值类型
        VERIFY_CODE_SCRIPT.setResultType(String.class);
    }

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存验证码到 Redis Hash，并设置 TTL。
     *
     * @param scene       场景名称。
     * @param identifier  标识（手机号或邮箱）。
     * @param code        验证码字符串。
     * @param ttl         有效期。
     * @param maxAttempts 最大尝试次数。
     * @throws RedisSystemException 保存失败时抛出。
     */
    @Override
    public void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts) {
        String key = buildKey(scene, identifier);
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        try {
            ops.put(key, FIELD_CODE, code);
            ops.put(key, FIELD_MAX_ATTEMPTS, String.valueOf(maxAttempts));
            ops.put(key, FIELD_ATTEMPTS, "0");
            redisTemplate.expire(key, ttl);
        } catch (DataAccessException ex) {
            throw new RedisSystemException("Failed to save verification code", ex);
        }
    }

    /**
     * 校验验证码是否匹配，更新尝试计数并在成功时删除记录。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @param code       用户输入的验证码。
     * @return 校验结果（成功、未找到、错误、尝试过多）。
     */
    @Override
    public VerificationCheckResult verify(String scene, String identifier, String code) {
        String key = buildKey(scene, identifier);

        String result = redisTemplate.execute(
                VERIFY_CODE_SCRIPT,
                List.of(key),
                code,
                String.valueOf(LOCK_TTL.toMillis())
        );

        return parseResult(result);
    }

    /**
     * 使验证码失效（删除存储记录）。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     */
    @Override
    public void invalidate(String scene, String identifier) {
        redisTemplate.delete(buildKey(scene, identifier));
    }

    /**
     * 生成验证码的 Redis 键名。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @return 键名字符串。
     */
    private static String buildKey(String scene, String identifier) {
        return "auth:code:%s:%s".formatted(scene, identifier);
    }

    /**
     * 解析整数字符串，失败返回默认值。
     *
     * @param value        待解析字符串。
     * @param defaultValue 解析失败时的默认值。
     * @return 整数值。
     */
    private static int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
    private VerificationCheckResult parseResult(String result) {
        if (result == null || result.isBlank()) {
            return new VerificationCheckResult(VerificationCodeStatus.NOT_FOUND, 0, 0);
        }

        String[] parts = result.split("\\|");
        String status = parts[0];
        int attempts = parts.length > 1 ? parseInt(parts[1], 0) : 0;
        int maxAttempts = parts.length > 2 ? parseInt(parts[2], 0) : 0;

        return switch (status) {
            case "SUCCESS" -> new VerificationCheckResult(VerificationCodeStatus.SUCCESS, attempts, maxAttempts);
            case "MISMATCH" -> new VerificationCheckResult(VerificationCodeStatus.MISMATCH, attempts, maxAttempts);
            case "TOO_MANY_ATTEMPTS" -> new VerificationCheckResult(VerificationCodeStatus.TOO_MANY_ATTEMPTS, attempts, maxAttempts);
            default -> new VerificationCheckResult(VerificationCodeStatus.NOT_FOUND, attempts, maxAttempts);
        };
    }


}

