package com.tongji.cache.hotkey;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableConfigurationProperties(HotKeyProperties.class)
public class HotKeyLocalConfig {


//    每一个热点key的缓存
    @Bean
    @Qualifier("localHotValueCache")
    public Cache<String, LocalValueModel> localHotValueCache(HotKeyProperties hotKeyProperties) {
        return Caffeine.newBuilder()
                .maximumSize(hotKeyProperties.getRegistryMaximumSize())
                .expireAfter(new Expiry<String, LocalValueModel>() {
                    @Override
                    public long expireAfterCreate(String key, LocalValueModel value, long currentTime) {
                        return TimeUnit.SECONDS.toNanos(value.getDuration());
                    }

                    @Override
                    public long expireAfterUpdate(String key, LocalValueModel value, long currentTime, long currentDuration) {
                        return TimeUnit.SECONDS.toNanos(value.getDuration());
                    }

                    @Override
                    public long expireAfterRead(String key, LocalValueModel value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

//    记录每一个key的计数器的缓存
    @Bean
    @Qualifier("localHotCounterCache")
    public Cache<String, SlidingWindowCounter> localHotCounterCache(HotKeyProperties hotKeyProperties) {
        return Caffeine.newBuilder()
                .maximumSize(hotKeyProperties.getCounterMaximumSize())
                .expireAfterAccess(hotKeyProperties.getCounterExpireAfterAccessSeconds(), TimeUnit.SECONDS)
                .build();
    }
}