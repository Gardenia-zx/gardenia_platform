package com.tongji.cache.hotkey;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JdStyleLocalHotKeyStore {

    private final LocalKeyRuleHolder localKeyRuleHolder;
    private final HotKeyDetector localHotKeyDetector;

    @Qualifier("localHotValueCache")
    private final Cache<String, LocalValueModel> localHotValueCache;

    private boolean isNearExpire(LocalValueModel valueModel) {
        if (valueModel == null) {
            return true;
        }
        return valueModel.getCreateTime() + valueModel.getDuration() - System.currentTimeMillis() <= 2000;
    }

    public boolean isHotKey(String key) {
        try {
            if (!inRule(key)) {
                return false;
            }

            boolean isHot = isHot(key);
            if (!isHot) {
                localHotKeyDetector.detectAndMark(key);
            } else {
                LocalValueModel valueModel = getValueSimple(key);
                if (isNearExpire(valueModel)) {
                    localHotKeyDetector.detectAndMark(key);
                }
            }

            return isHot(key);
        } catch (Exception e) {
            return false;
        }
    }

    public Object get(String key) {
        LocalValueModel value = getValueSimple(key);
        if (value == null) {
            return null;
        }

        Object object = value.getValue();
        if (object instanceof Integer && LocalValueModel.MAGIC_NUMBER == (int) object) {
            return null;
        }
        return object;
    }

    public void smartSet(String key, Object value) {
        if (isHot(key)) {
            LocalValueModel valueModel = getValueSimple(key);
            if (valueModel == null) {
                return;
            }
            valueModel.setValue(value);
        }
    }

    public Object getValue(String key) {
        try {
            if (!inRule(key)) {
                return null;
            }

            Object userValue = null;
            LocalValueModel value = getValueSimple(key);

            if (value == null) {
                localHotKeyDetector.detectAndMark(key);
            } else {
                if (isNearExpire(value)) {
                    localHotKeyDetector.detectAndMark(key);
                }

                Object object = value.getValue();
                if (object instanceof Integer && LocalValueModel.MAGIC_NUMBER == (int) object) {
                    userValue = null;
                } else {
                    userValue = object;
                }
            }

            return userValue;
        } catch (Exception e) {
            return null;
        }
    }

    public void forceSet(String key, Object value) {
        LocalValueModel valueModel = LocalValueModel.defaultValue(key, localKeyRuleHolder);
        if (valueModel != null) {
            valueModel.setValue(value);
            setValueDirectly(key, valueModel);
        }
    }

    public void remove(String key) {
        localHotValueCache.invalidate(key);
        localHotKeyDetector.remove(key);
    }

    public boolean isBlacklisted(String key) {
        return isHotKey(key) && localKeyRuleHolder.findRule(key) != null
                && localKeyRuleHolder.findRule(key).getAction() == HotAction.BLACKLIST;
    }

    private LocalValueModel getValueSimple(String key) {
        return localHotValueCache.getIfPresent(key);
    }

    private void setValueDirectly(String key, LocalValueModel value) {
        localHotValueCache.put(key, value);
    }

    private boolean isHot(String key) {
        return getValueSimple(key) != null;
    }

    private boolean inRule(String key) {
        return localKeyRuleHolder.isKeyInRule(key);
    }
}