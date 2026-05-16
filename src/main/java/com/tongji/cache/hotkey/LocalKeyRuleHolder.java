package com.tongji.cache.hotkey;


import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalKeyRuleHolder {

    private final HotKeyProperties hotKeyProperties;

    public HotKeyProperties.Rule findRule(String key){
//        校验key是否有效
        if(key == null||key.isBlank()){
            return null;
        }
//        匹配的级别，全匹配>前缀匹配>通配符匹配(兜底)
        HotKeyProperties.Rule preFixMatchRule = null;
        HotKeyProperties.Rule wildcardMatchRule = null;
        for(HotKeyProperties.Rule rule:hotKeyProperties.getRules()){
//            校验rule是否合法
            if(rule.getKeyPattern() == null||rule.getKeyPattern().isBlank()){
                continue;
            }
//            全匹配
            if(rule.getKeyPattern().equals(key)){
                return rule;
            }
//            前缀匹配
            if(rule.isPrefixMatch()&&key.startsWith(rule.getKeyPattern())){
                preFixMatchRule = rule;
            }
//            通配
            if("*".equals(rule.getKeyPattern())){
                wildcardMatchRule = rule;
            }
        }
        if(preFixMatchRule != null){
            return preFixMatchRule;
        }
        return wildcardMatchRule;
    }

    public boolean isKeyInRule(String key){
        return findRule(key) != null;
    }

    public  int duration(String key){
        HotKeyProperties.Rule rule = findRule(key);
        if(rule == null){
            return 0;
        }
        return rule.getHotTtlSeconds();
    }
}
