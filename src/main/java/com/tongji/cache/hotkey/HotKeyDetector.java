package com.tongji.cache.hotkey;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;


/**
 * 热键探测器（滑动时间窗口计数 + 热度分级 + TTL 动态扩展）。
 * <p>
 * 设计说明：
 * - 采用固定分段滑动窗口：窗口长度 windowSeconds，分段长度 segmentSeconds，段数 segments=window/segment；
 * - 每个 key 维护长度为 segments 的数组 counters[key]，current 指向当前活跃段；
 * - 周期性 rotate 将 current 前移并清零新段，实现近窗口热度的自然衰减；
 * - 根据总热度 h=Σ段计数，映射到 NONE/LOW/MEDIUM/HIGH 的热度等级；
 * - 提供 ttlForPublic/ttlForMine：在基准 TTL 上叠加等级扩展秒数，保护热点请求。
 * <p>
 * 并发语义：
 * - 使用 ConcurrentHashMap 存储计数数组，AtomicInteger 维护段游标；
 * - 计数递增为无锁数组操作，rotate 仅清零新段，避免大范围写冲突；
 * - 统计为近似滑窗，保证在高并发下的稳定与低开销。
 */

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HotKeyDetector {

    private final LocalKeyRuleHolder localKeyRuleHolder;

    @Qualifier("localHotCounterCache")
    private final Cache<String, SlidingWindowCounter> localHotCounterCache;

    @Qualifier("localHotValueCache")
    private final Cache<String, LocalValueModel> localHotValueCache;

    public void detectAndMark(String key) {
        HotKeyProperties.Rule rule = localKeyRuleHolder.findRule(key);
        if (rule == null) {
            return;
        }

        SlidingWindowCounter counter = localHotCounterCache.get(
                key,
                k -> new SlidingWindowCounter(rule.getIntervalSeconds(), rule.getThreshold())
        );

        if (counter == null) {
            return;
        }

        boolean hot = counter.addCount(1);
        if (hot) {
            LocalValueModel old = localHotValueCache.getIfPresent(key);
            if (old == null) {
                LocalValueModel valueModel = LocalValueModel.defaultValue(key, localKeyRuleHolder);
                if (valueModel != null) {
                    localHotValueCache.put(key, valueModel);
                }
            }
        }
    }

    public void remove(String key) {
        localHotCounterCache.invalidate(key);
        localHotValueCache.invalidate(key);
    }
}

