package com.dpp.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.gateway.server.webflux.discovery.locator.enabled=false",
        "spring.cloud.gateway.server.webflux.routes[0].id=test-upstream",
        "spring.cloud.gateway.server.webflux.routes[0].uri=http://127.0.0.1:9",
        "spring.cloud.gateway.server.webflux.routes[0].predicates[0]=Path=/api/test/**",
        "spring.cloud.gateway.server.webflux.routes[0].filters[0]=StripPrefix=1"
})
@Import(GatewayIntegrationTest.TestJwtConfiguration.class)
class GatewayIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private KeyPair testKeyPair;

    @Test
    void jwtAuthenticationFilterRejectsRequestsWithoutBearerToken() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/test/ping", HttpMethod.GET, HttpEntity.EMPTY, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void roleFilterRejectsInsufficientRole() {
        String jwt = JwtTestSupport.sign("user", List.of("USER"), 60, testKeyPair);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                "/admin/products", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "x"), headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @TestConfiguration
    static class TestJwtConfiguration {

        @Bean
        KeyPair testKeyPair() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        @Bean
        @Primary
        ReactiveJwtDecoder testJwtDecoder(KeyPair testKeyPair) {
            return NimbusReactiveJwtDecoder.withPublicKey((RSAPublicKey) testKeyPair.getPublic()).build();
        }
    }
}
