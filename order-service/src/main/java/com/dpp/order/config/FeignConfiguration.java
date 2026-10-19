package com.dpp.order.config;

import com.dpp.common.security.InternalServiceToken;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfiguration {

    @Bean
    public RequestInterceptor internalServiceTokenInterceptor(
            @Value("${dpp.internal.service-token:}") String serviceToken) {
        return template -> {
            if (serviceToken != null && !serviceToken.isBlank()) {
                template.header(InternalServiceToken.HEADER, serviceToken);
            }
        };
    }
}
