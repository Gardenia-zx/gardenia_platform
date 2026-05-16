package com.tongji.auth.config;
import com.tongji.auth.token.AccessTokenBlacklistStore;
import com.tongji.auth.token.JwtService;
import com.tongji.auth.token.UserAccessTokenValidityStore;
import jakarta.annotation.Resource;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/**
 * 资源访问专用 JWT Converter。
 *
 * 只允许 token_type=access 的 JWT 进入受保护接口；
 * refresh token 即使签名合法，也不能作为 Bearer Token 访问业务资源。
 */
@Component
public class AccessOnlyJwtAuthenticationConverter implements Converter<Jwt, JwtAuthenticationToken> {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final JwtService jwtService;
    private final AccessTokenBlacklistStore accessTokenBlacklistStore;
    private final UserAccessTokenValidityStore userAccessTokenValidityStore;
    private final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

    public AccessOnlyJwtAuthenticationConverter(JwtService jwtService,
                                                AccessTokenBlacklistStore accessTokenBlacklistStore,
                                                UserAccessTokenValidityStore userAccessTokenValidityStore){
        this.jwtService = jwtService;
        this.accessTokenBlacklistStore  = accessTokenBlacklistStore;
        this.userAccessTokenValidityStore = userAccessTokenValidityStore;
    }
    @Override
    public JwtAuthenticationToken convert(Jwt jwt) {
        String tokenType = jwt.getClaimAsString(CLAIM_TOKEN_TYPE);
//        指定token_type == access
        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token", "Only access token can be used for resource access", null)
            );
        }
        String jti = jwtService.extractTokenId(jwt);
        if (accessTokenBlacklistStore.isBlacklisted(jti)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token", "Access token has been revoked", null)
            );
        }

        long userId = jwtService.extractUserId(jwt);
        Instant issuedAt = jwtService.extractIssuedAt(jwt);
        Optional<Instant> validAfter = userAccessTokenValidityStore.getValidAfter(userId);

        if (validAfter.isPresent() && issuedAt.isBefore(validAfter.get())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token", "Token was issued before user's last security event", null)
            );
        }
        Collection<GrantedAuthority> authorities = authoritiesConverter.convert(jwt);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }


}