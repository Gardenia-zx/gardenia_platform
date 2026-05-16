package com.tongji.cache.hotkey;


import lombok.Getter;

import lombok.Setter;

@Getter
@Setter
public class LocalValueModel {

    /**
     * 创建时间
     */
    private long createTime = System.currentTimeMillis();

    /**
     * 本地缓存时长，单位毫秒
     */
    private int duration;

    /**
     * 实际业务 value
     */
    private Object value;

    /**
     * 默认占位值，表示“这个 key 已经是热点，但业务 value 还没填”
     */
    public static final int MAGIC_NUMBER = -20260421;

    public static LocalValueModel defaultValue(String key, LocalKeyRuleHolder ruleHolder) {
        HotKeyProperties.Rule rule = ruleHolder.findRule(key);
        if (rule == null) {
            return null;
        }

        LocalValueModel valueModel = new LocalValueModel();
        valueModel.setDuration(rule.getHotTtlSeconds() * 1000);
        valueModel.setValue(MAGIC_NUMBER);
        return valueModel;
    }
}
//    是不是需要根据不同的类型加上不同的数据，比如：经常访问的热点内容，这个info里面是不是要存热点的具体信息