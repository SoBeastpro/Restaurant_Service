package com.restaurant.identity.auth.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class JwtConfiguration {

    @Bean
    JwtKeyMaterial jwtKeyMaterial(AuthProperties properties) {
        Resource privateKey = properties.getPrivateKeyLocation();
        Resource publicKey = properties.getPublicKeyLocation();
        if (privateKey == null && publicKey == null) {
            return generatedKey(properties.getKeyId());
        }
        if (privateKey == null || publicKey == null) {
            throw new IllegalStateException("Both JWT private and public key locations must be configured");
        }
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateKey parsedPrivateKey = (RSAPrivateKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(readPem(privateKey, "PRIVATE KEY")));
            RSAPublicKey parsedPublicKey = (RSAPublicKey) factory.generatePublic(
                    new X509EncodedKeySpec(readPem(publicKey, "PUBLIC KEY")));
            return new JwtKeyMaterial(parsedPublicKey, parsedPrivateKey, properties.getKeyId());
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Cannot load JWT signing keys", exception);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyMaterial keys) {
        return NimbusJwtEncoder.withKeyPair(keys.publicKey(), keys.privateKey())
                .algorithm(SignatureAlgorithm.RS256)
                .jwkPostProcessor(builder -> builder.keyID(keys.keyId()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyMaterial keys, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.getIssuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.getAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Required token audience is missing", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    private static JwtKeyMaterial generatedKey(String keyId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return new JwtKeyMaterial(
                    (RSAPublicKey) pair.getPublic(),
                    (RSAPrivateKey) pair.getPrivate(),
                    keyId + "-" + java.util.UUID.randomUUID());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot generate a local JWT signing key", exception);
        }
    }

    private static byte[] readPem(Resource resource, String type) throws IOException {
        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        }
        String encoded = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded);
    }
}
