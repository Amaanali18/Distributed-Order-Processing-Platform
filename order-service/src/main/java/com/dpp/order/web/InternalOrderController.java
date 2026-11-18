package com.dpp.order.web;

import com.dpp.order.service.OrderService;
import com.dpp.order.web.dto.OrderResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service view of an order. Protected by the shared internal token,
 * not by a customer JWT: payment-service uses it to verify ownership, amount
 * and lifecycle state before creating a payment attempt.
 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.getInternal(id);
    }
}
