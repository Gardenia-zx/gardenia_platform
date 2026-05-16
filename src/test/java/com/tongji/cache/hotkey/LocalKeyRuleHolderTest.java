package com.tongji.cache.hotkey;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalKeyRuleHolderTest {

    private LocalKeyRuleHolder localKeyRuleHolder;

    @BeforeEach
    void setUp() {
        HotKeyProperties properties = new HotKeyProperties();

        HotKeyProperties.Rule exactRule = new HotKeyProperties.Rule();
        exactRule.setKeyPattern("hot:api:/api/v1/knowposts/feed");
        exactRule.setPrefixMatch(false);
        exactRule.setIntervalSeconds(1);
        exactRule.setThreshold(100);
        exactRule.setHotTtlSeconds(10);
        exactRule.setAction(HotAction.RATE_LIMIT);

        HotKeyProperties.Rule prefixRule = new HotKeyProperties.Rule();
        prefixRule.setKeyPattern("hot:obj:knowpost:");
        prefixRule.setPrefixMatch(true);
        prefixRule.setIntervalSeconds(2);
        prefixRule.setThreshold(10);
        prefixRule.setHotTtlSeconds(60);
        prefixRule.setAction(HotAction.CACHE);

        HotKeyProperties.Rule wildcardRule = new HotKeyProperties.Rule();
        wildcardRule.setKeyPattern("*");
        wildcardRule.setPrefixMatch(false);
        wildcardRule.setIntervalSeconds(5);
        wildcardRule.setThreshold(999);
        wildcardRule.setHotTtlSeconds(5);
        wildcardRule.setAction(HotAction.DEFAULT_VALUE);

        properties.setRules(List.of(exactRule, prefixRule, wildcardRule));
        localKeyRuleHolder = new LocalKeyRuleHolder(properties);
    }

    @Test
    void should_match_exact_rule_first() {
        HotKeyProperties.Rule rule = localKeyRuleHolder.match("hot:api:/api/v1/knowposts/feed");
        assertNotNull(rule);
        assertEquals("hot:api:/api/v1/knowposts/feed", rule.getKeyPattern());
        assertEquals(HotAction.RATE_LIMIT, rule.getAction());
    }

    @Test
    void should_match_prefix_rule() {
        HotKeyProperties.Rule rule = localKeyRuleHolder.match("hot:obj:knowpost:123");
        assertNotNull(rule);
        assertEquals("hot:obj:knowpost:", rule.getKeyPattern());
        assertEquals(HotAction.CACHE, rule.getAction());
    }

    @Test
    void should_match_wildcard_rule_when_no_other_rule_matches() {
        HotKeyProperties.Rule rule = localKeyRuleHolder.match("abc:def");
        assertNotNull(rule);
        assertEquals("*", rule.getKeyPattern());
        assertEquals(HotAction.DEFAULT_VALUE, rule.getAction());
    }

    @Test
    void should_return_null_when_key_is_blank() {
        assertNull(localKeyRuleHolder.match(null));
        assertNull(localKeyRuleHolder.match(""));
        assertNull(localKeyRuleHolder.match("   "));
    }
}