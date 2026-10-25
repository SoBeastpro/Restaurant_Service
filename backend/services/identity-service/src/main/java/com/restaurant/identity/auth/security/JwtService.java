package com.restaurant.identity.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final JwtKeyMaterialAccessor keys;
    private final Clock clock;

    @Autowired
    public JwtService(JwtEncoder encoder, AuthProperties properties, JwtKeyMaterialAccessor keys) {
        this(encoder, properties, keys, Clock.systemUTC());
    }

    JwtService(
            JwtEncoder encoder,
            AuthProperties properties,
            JwtKeyMaterialAccessor keys,
            Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.keys = keys;
        this.clock = clock;
    }

    public AccessToken issue(
            UUID userId,
            UUID sessionId,
            String login,
            String role,
            String position,
            UUID restaurantId,
            List<String> platformRoles) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.getAccessTokenTtl());
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keys.keyId())
                .build();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .audience(List.of(properties.getAudience()))
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId.toString())
                .claim("login", login)
                .claim("role", role)
                .claim("roles", List.of(role))
                .claim("platform_roles", platformRoles)
                ;
        if (restaurantId != null) {
            claims.claim("restaurant_id", restaurantId.toString());
        }
        if (position != null) {
            claims.claim("position", position);
        }
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AccessToken(value, properties.getAccessTokenTtl().toSeconds());
    }

    public record AccessToken(String value, long expiresInSeconds) {
    }
}
