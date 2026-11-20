package com.dpp.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.security.Principal;

@Configuration
public class RateLimitConfiguration {

    /**
     * Authenticated requests share a bucket per user. Public requests (catalogue,
     * webhooks, actuator) fall back to the client IP so one anonymous caller
     * cannot exhaust another caller's allowance.
     */
    @Bean
    public KeyResolver rateLimitKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .switchIfEmpty(Mono.fromSupplier(() -> clientIp(exchange.getRequest().getRemoteAddress())))
                .defaultIfEmpty("anonymous");
    }

    private String clientIp(InetSocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "anonymous";
        }
        return "ip:" + remoteAddress.getAddress().getHostAddress();
    }
}
