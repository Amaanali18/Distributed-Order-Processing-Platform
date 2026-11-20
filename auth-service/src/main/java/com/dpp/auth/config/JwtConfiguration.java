package com.dpp.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Set;
import java.util.UUID;

@Configuration
public class JwtConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtConfiguration.class);
    private static final String DEFAULT_JWK_PATH = "./keys/auth-jwk.json";

    @Bean
    public RSAKey rsaKey(JwtProperties properties) throws Exception {
        Path path = Path.of(properties.jwkPath() == null || properties.jwkPath().isBlank()
                ? DEFAULT_JWK_PATH
                : properties.jwkPath());

        if (Files.exists(path)) {
            return load(path);
        }

        boolean generate = properties.generateIfMissing();
        if (!generate) {
            throw new IllegalStateException(
                    "JWT signing key not found at " + path.toAbsolutePath()
                            + " and dpp.jwt.generate-if-missing=false");
        }

        RSAKey generated = generate();
        return persist(path, generated);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, context) -> jwkSelector.select(jwkSet);
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) throws Exception {
        return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
    }

    private RSAKey load(Path path) throws IOException, ParseException {
        RSAKey key = RSAKey.parse(Files.readString(path));
        if (!key.isPrivate()) {
            throw new IllegalStateException(
                    "JWT JWK at " + path.toAbsolutePath() + " has no private key");
        }
        log.info("Loaded persistent JWT signing key from {}", path.toAbsolutePath());
        return key;
    }

    private RSAKey generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }

    private RSAKey persist(Path path, RSAKey key) throws IOException, ParseException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try {
            Files.writeString(absolute, key.toJSONString(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException e) {
            // Another instance created it first; use that key so tokens stay valid.
            return load(path);
        }
        try {
            Files.setPosixFilePermissions(absolute,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows filesystems do not support POSIX permissions.
        }
        log.warn("Generated a new JWT signing key at {}. Back it up or mount the same file on restart; "
                + "otherwise previously issued tokens will no longer validate.", absolute);
        return key;
    }
}
