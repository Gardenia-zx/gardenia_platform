package com.tongji.cache.hotkey;


import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalHotKeyRegistry {

    @Qualifier("hotRegistryCache")
    private final Cache<String, HotKeyModel> hotRegistryCache;

//    判断key是否在热点表里面
    public boolean isHot(String key){
        return hotRegistryCache.getIfPresent(key) !=null;
    }

//    获取热点信息
    public HotKeyModel getHotKeyInfo(String key){
        return hotRegistryCache.getIfPresent(key);
    }
//    添加热点
    public void addHotKey(String key, HotKeyProperties.Rule rule){
        HotKeyModel hotKeyModel = new HotKeyModel(key,rule.getAction(),rule.getHotTtlSeconds(),System.currentTimeMillis());
        hotRegistryCache.put(key, hotKeyModel);
    }

//    移除热点
    public void removeHotKey(String key){
        hotRegistryCache.invalidate(key);
    }
}
