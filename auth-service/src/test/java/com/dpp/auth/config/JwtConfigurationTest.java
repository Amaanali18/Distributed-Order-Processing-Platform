package com.dpp.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigurationTest {

    @TempDir
    Path tempDir;

    private JwtProperties properties(Path path, boolean generate) {
        return new JwtProperties("dpp-auth", Duration.ofMinutes(15), Duration.ofDays(7),
                path.toString(), generate);
    }

    @Test
    void generatedKeyIsPersistedAndReusedOnRestart() throws Exception {
        Path path = tempDir.resolve("nested/auth-jwk.json");
        JwtConfiguration configuration = new JwtConfiguration();

        RSAKey first = configuration.rsaKey(properties(path, true));
        RSAKey second = configuration.rsaKey(properties(path, true));

        assertThat(Files.exists(path)).isTrue();
        assertThat(second.getKeyID()).isEqualTo(first.getKeyID());
        assertThat(second.toRSAPublicKey()).isEqualTo(first.toRSAPublicKey());
    }

    @Test
    void missingKeyFailsClosedWhenGenerationIsDisabled() {
        Path path = tempDir.resolve("missing/auth-jwk.json");

        assertThatThrownBy(() -> new JwtConfiguration().rsaKey(properties(path, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generate-if-missing=false");
    }
}
