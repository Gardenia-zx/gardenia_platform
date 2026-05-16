package com.tongji.auth.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface UserAccessTokenValidityStore {

    void setValidAfter(long userId, Instant validAfter, Duration ttl);

    Optional<Instant> getValidAfter(long userId);
}