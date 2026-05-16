package com.tongji.auth.verification;

import lombok.RequiredArgsConstructor;
import com.tongji.auth.config.AuthProperties;
import com.tongji.common.exception.BusinessException;
import com.tongji.common.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 验证码业务服务。
 * <p>
 * 负责发送与校验验证码：
 * - 速率限制与日限额；
 * - 随机码生成与存储；
 * - 调用发送器进行实际发送；
 * 配置来源于 `AuthProperties.Verification`。
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DefaultRedisScript<Long> RESERVE_SEND_CODE_SCRIPT;
    private static final DefaultRedisScript<Long> RELEASE_SEND_CODE_SCRIPT;

    static {
        RESERVE_SEND_CODE_SCRIPT = new DefaultRedisScript<>();
        RESERVE_SEND_CODE_SCRIPT.setLocation(new ClassPathResource("scripts/auth/reserve_send_code.lua"));
        RESERVE_SEND_CODE_SCRIPT.setResultType(Long.class);

        RELEASE_SEND_CODE_SCRIPT = new DefaultRedisScript<>();
        RELEASE_SEND_CODE_SCRIPT.setLocation(new ClassPathResource("scripts/auth/release_send_code_reservation.lua"));
        RELEASE_SEND_CODE_SCRIPT.setResultType(Long.class);
    }


    private final VerificationCodeStore codeStore;
    private final CodeSender codeSender;
    private final StringRedisTemplate stringRedisTemplate;
    private final AuthProperties properties;

    /**
     * 发送验证码到指定标识。
     * <p>
     * 执行发送间隔与日次数限制，生成随机数字验证码，保存到存储并调用发送器。
     *
     * @param scene      验证码场景（REGISTER/LOGIN/RESET_PASSWORD）。
     * @param identifier 标识（手机号或邮箱）。
     * @return 发送结果，包含标识、场景与过期秒数。
     * @throws BusinessException 参数不完整或触发速率/日限额时抛出。
     */
    public SendCodeResult sendCode(VerificationScene scene, String identifier) {
        if (scene == null || !StringUtils.hasText(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供正确的验证码发送参数");
        }
        AuthProperties.Verification cfg = properties.getVerification();
        reserveSendQuota(scene,identifier,cfg.getTtl(),cfg.getDailyLimit());

        String code = generateNumericCode(cfg.getCodeLength());
        try {
            codeStore.saveCode(scene.name(), identifier, code, cfg.getTtl(), cfg.getMaxAttempts());
            codeSender.sendCode(scene, identifier, code, (int) cfg.getTtl().toMinutes());
            return new SendCodeResult(identifier, scene, (int) cfg.getTtl().toSeconds());
        } catch (Exception e) {
//            失败回滚
            invalidate(scene,identifier);//删hash
            releaseSendQuota(scene,identifier);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码发送失败，请稍后重试");
        }
    }


    private void releaseSendQuota(VerificationScene scene, String identifier) {
        String lastKey ="auth:code:Last:" + scene.name() + ":" + identifier;
        String date = DAY_FORMAT.format(LocalDate.now());
        String dailyKey = "auth:code:count:" + scene.name() + ":" + identifier + "." + date;

        stringRedisTemplate.execute(
                RELEASE_SEND_CODE_SCRIPT,
                List.of(lastKey, dailyKey)
        );
    }

    /**
     * 校验验证码是否正确且未超限。
     *
     * @param scene      验证码场景。
     * @param identifier 标识（手机号或邮箱）。
     * @param code       用户输入的验证码。
     * @return 校验结果，包含状态与尝试次数统计。
     * @throws BusinessException 参数不完整时抛出。
     */
    public VerificationCheckResult verify(VerificationScene scene, String identifier, String code) {
        if (scene == null || !StringUtils.hasText(identifier) || !StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验参数不完整");
        }
        return codeStore.verify(scene.name(), identifier, code);
    }

    /**
     * 使验证码失效（删除存储记录）。
     *
     * @param scene      验证码场景。
     * @param identifier 标识（手机号或邮箱）。
     */
    public void invalidate(VerificationScene scene, String identifier) {
        codeStore.invalidate(scene.name(), identifier);
    }

    /**
     * 原子占用发送资格：
     * 1. 校验发送冷却
     * 2. 校验每日限额
     * 3. 成功后占用冷却窗口 + 日计数
     */
    private void reserveSendQuota(VerificationScene scene, String identifier, Duration sendInterval, int dailyLimit) {
        long cooldownMs = sendInterval != null ? Math.max(sendInterval.toMillis(), 0L) : 0L;
        long dailyTtlMs = Duration.ofDays(1).toMillis();

        String lastKey = "auth:code:last:" + scene.name() + ":" + identifier;
        String date = DAY_FORMAT.format(LocalDate.now());
        String dailyKey = "auth:code:count:" + scene.name() + ":" + identifier + ":" + date;

        Long result = stringRedisTemplate.execute(
                RESERVE_SEND_CODE_SCRIPT,
                List.of(lastKey, dailyKey),
                String.valueOf(cooldownMs),
                String.valueOf(dailyLimit),
                String.valueOf(dailyTtlMs)
        );

        if (Objects.equals(1L, result)) {
            throw new BusinessException(ErrorCode.VERIFICATION_RATE_LIMIT);
        }
        if (Objects.equals(2L, result)) {
            throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT);
        }
    }


    /**
     * 生成指定长度的纯数字验证码。
     *
     * @param length 验证码长度。
     * @return 数字字符串。
     */
    private static String generateNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
