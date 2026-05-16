package com.tongji.auth.token;

import java.time.Duration;

public interface AccessTokenBlacklistStore {

    void blacklist(String tokenId, Duration ttl);

    boolean isBlacklisted(String tokenId);
}