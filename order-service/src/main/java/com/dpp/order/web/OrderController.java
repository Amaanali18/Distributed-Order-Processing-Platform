package com.dpp.order.web;

import com.dpp.common.web.CorrelationId;
import com.dpp.order.service.OrderService;
import com.dpp.order.web.dto.CreateOrderRequest;
import com.dpp.order.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request,
                                @AuthenticationPrincipal Jwt jwt) {
        return orderService.create(request, CurrentUser.id(jwt), CurrentUser.email(jwt),
                CorrelationId.current());
    }

    @GetMapping
    public Page<OrderResponse> list(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        return orderService.listMine(CurrentUser.id(jwt), pageable);
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orderService.get(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return orderService.cancel(id, CurrentUser.id(jwt), CurrentUser.isAdmin(jwt), CorrelationId.current());
    }
}
