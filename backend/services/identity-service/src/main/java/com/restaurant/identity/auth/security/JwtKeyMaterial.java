package com.restaurant.identity.auth.security;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

record JwtKeyMaterial(RSAPublicKey publicKey, RSAPrivateKey privateKey, String keyId) {
}
