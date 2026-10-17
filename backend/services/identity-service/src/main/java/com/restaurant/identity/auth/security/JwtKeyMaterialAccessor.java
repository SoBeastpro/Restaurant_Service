package com.restaurant.identity.auth.security;

import java.security.interfaces.RSAPublicKey;

import org.springframework.stereotype.Component;

@Component
public class JwtKeyMaterialAccessor {

    private final JwtKeyMaterial keys;

    JwtKeyMaterialAccessor(JwtKeyMaterial keys) {
        this.keys = keys;
    }

    public RSAPublicKey publicKey() {
        return keys.publicKey();
    }

    public String keyId() {
        return keys.keyId();
    }
}
