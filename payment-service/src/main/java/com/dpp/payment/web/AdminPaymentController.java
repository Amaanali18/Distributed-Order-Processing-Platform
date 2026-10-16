package com.dpp.payment.web;

import com.dpp.common.web.CorrelationId;
import com.dpp.payment.service.PaymentService;
import com.dpp.payment.web.dto.RefundRequest;
import com.dpp.payment.web.dto.RefundResponse;
import jakarta.validation.Valid;
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

    @PostMapping("/{id}/refund")
    public RefundResponse refund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return paymentService.refund(id, request, CorrelationId.current());
    }
}
