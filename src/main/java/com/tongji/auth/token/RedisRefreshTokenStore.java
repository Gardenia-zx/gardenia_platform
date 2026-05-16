package com.tongji.auth.token;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 基于 Redis 的刷新令牌白名单存储。
 * <p>
 * 键空间：`auth:rt:{userId}:{tokenId}`，值固定为 "1"，设置 TTL 控制过期。
 * 支持校验令牌有效性、撤销单个令牌或撤销某用户全部令牌。
 */
@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final DefaultRedisScript<Long> ROTATE_SCRIPT;

    static {
        ROTATE_SCRIPT = new DefaultRedisScript<>();
        ROTATE_SCRIPT.setLocation(new ClassPathResource("scripts/auth/refresh_rotate.lua"));
        ROTATE_SCRIPT.setResultType(Long.class);
    }

    private static final int SCAN_COUNT  = 10;
    private static final int DELETE_BATCH_SIZE = 40;
    private final StringRedisTemplate redisTemplate;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将刷新令牌写入白名单，设置过期时间。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     * @param ttl     生存时间（Redis TTL）。
     */
    @Override
    public void storeToken(long userId, String tokenId, Duration ttl) {
        String key = key(userId, tokenId);
        redisTemplate.opsForValue().set(key, "1", ttl);
    }

    /**
     * 判断刷新令牌是否仍有效。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     * @return 是否有效（键存在且值为 "1"）。
     */
    @Override
    public boolean isTokenValid(long userId, String tokenId) {
        String key = key(userId, tokenId);
        return Objects.equals("1", redisTemplate.opsForValue().get(key));
    }

    /**
     * 撤销单个刷新令牌。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     */
    @Override
    public void revokeToken(long userId, String tokenId) {
        redisTemplate.delete(key(userId, tokenId));
    }

    /**
     * 撤销该用户全部刷新令牌。
     *
     * @param userId 用户 ID。
     */
    @Override
    public void revokeAll(long userId) {
        String pattern = "auth:rt:%d:*".formatted(userId);
        var keys = redisTemplate.keys(pattern);
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * 生成白名单键名。
     *
     * @param userId  用户 ID。
     * @param tokenId 刷新令牌 ID。
     * @return Redis 键名。
     */

    private static String key(long userId, String tokenId) {
        return "auth:rt:%d:%s".formatted(userId, tokenId);
    }

    @Override
    public boolean rotateToken(long userId, String oldTokenId, String newTokenId, Duration newTtl) {
        if (!StringUtils.hasText(oldTokenId)
                || !StringUtils.hasText(newTokenId)
                || newTtl == null
                || !newTtl.isPositive()) {
            return false;
        }

        String oldKey = key(userId, oldTokenId);
        String newKey = key(userId, newTokenId);

        Long result = redisTemplate.execute(
                ROTATE_SCRIPT,
                List.of(oldKey, newKey),
                String.valueOf(newTtl.toMillis())
        );

        return Objects.equals(1L, result);
    }

    /**
     * 分批读取key，删除refreshToken
     * @param userId 用户 ID。
     */
    @Override
    public void revokeAllByScan(long userId) {
        String pattern = "auth:rt:%d:*".formatted(userId);

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(SCAN_COUNT)
                    .build();

            List<String> batch = new ArrayList<>(DELETE_BATCH_SIZE);

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    String key = new String(cursor.next(), StandardCharsets.UTF_8);
                    batch.add(key);

                    if (batch.size() >= DELETE_BATCH_SIZE) {
                        redisTemplate.delete(batch);
                        batch.clear();
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to revoke all refresh tokens by SCAN", e);
            }

            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }

            return null;
        });
    }
}
