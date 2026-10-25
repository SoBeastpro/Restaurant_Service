package com.restaurant.identity.auth.api;

import java.util.List;
import java.util.Map;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.restaurant.identity.auth.security.JwtKeyMaterialAccessor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class JwkSetController {

    private final JwtKeyMaterialAccessor keys;

    JwkSetController(JwtKeyMaterialAccessor keys) {
        this.keys = keys;
    }

    @GetMapping("/.well-known/jwks.json")
    Map<String, Object> jwks() {
        RSAKey publicKey = new RSAKey.Builder(keys.publicKey())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(keys.keyId())
                .build();
        return Map.of("keys", List.of(publicKey.toJSONObject()));
    }
}
