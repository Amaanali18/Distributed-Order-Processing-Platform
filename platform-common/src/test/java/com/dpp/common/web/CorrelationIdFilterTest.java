package com.dpp.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesCorrelationIdWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String header = response.getHeader(CorrelationId.HEADER);
        assertThat(header).isNotBlank();
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    void reusesIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/products");
        request.addHeader(CorrelationId.HEADER, "corr-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(CorrelationId.HEADER)).isEqualTo("corr-abc");
    }
}
