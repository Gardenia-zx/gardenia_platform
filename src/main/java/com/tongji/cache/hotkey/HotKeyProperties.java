package com.tongji.cache.hotkey;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "cache.hotkey")
public class HotKeyProperties {

    /**
     * 计数器缓存最大数量
     */
    private long counterMaximumSize = 100_000;

    /**
     * 某个 key 长时间没有访问后，计数器自动淘汰
     */
    private long counterExpireAfterAccessSeconds = 180;

    /**
     * 热点表最大数量
     */
    private long registryMaximumSize = 50_000;

    /**
     * 热点探测规则
     */
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {

        /**
         * 规则匹配模式
         * 例如：hot:obj:knowpost:
         */
        private String keyPattern;

        /**
         * 是否按前缀匹配
         */
        private boolean prefixMatch = true;

        /**
         * 滑动窗口时长，单位秒
         */
        private int intervalSeconds = 2;

        /**
         * 窗口内达到多少次算热点
         */
        private int threshold = 10;

        /**
         * 成为热点后，在本地热点表里保留多久
         */
        private int hotTtlSeconds = 60;

        /**
         * 热点动作
         */
        private HotAction action = HotAction.CACHE;
    }
}