package com.dpp.payment.web;

import com.dpp.common.web.CorrelationId;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.web.dto.PaymentResponse;
import com.dpp.payment.web.dto.RefundRequest;
import com.dpp.payment.web.dto.RefundResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public Page<PaymentResponse> list(Pageable pageable) {
        return paymentService.listAll(pageable);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable UUID id) {
        return paymentService.get(id, null, true);
    }

    @PostMapping("/{id}/refund")
    public RefundResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return paymentService.refund(id, request, CorrelationId.current());
    }
}
