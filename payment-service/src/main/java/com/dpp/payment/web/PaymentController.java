package com.dpp.payment.web;

import com.dpp.common.web.CorrelationId;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.web.dto.ConfirmPaymentRequest;
import com.dpp.payment.web.dto.CreatePaymentRequest;
import com.dpp.payment.web.dto.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest request,
                                  @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                  @AuthenticationPrincipal Jwt jwt) {
        return paymentService.create(request, CurrentUser.id(jwt), idempotencyKey);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return paymentService.get(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt));
    }

    @GetMapping("/by-order/{orderId}")
    public PaymentResponse findByOrder(@PathVariable UUID orderId) {
        return paymentService.findByOrder(orderId);
    }

    @PostMapping("/{id}/confirm")
    public PaymentResponse confirm(@PathVariable UUID id,
                                   @Valid @RequestBody ConfirmPaymentRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return paymentService.confirm(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt),
                request, CorrelationId.current());
    }
}
