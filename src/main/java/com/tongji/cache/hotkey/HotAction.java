package com.tongji.cache.hotkey;

public enum HotAction {
//    热点内容进本地缓存，增加ttl
    CACHE,
//    爬虫，恶意请求拦截写入黑名单
    BLACKLIST,
//    接口防刷限流
    RATE_LIMIT,
//    降级返回默认值
    DEFAULT_VALUE;
}
