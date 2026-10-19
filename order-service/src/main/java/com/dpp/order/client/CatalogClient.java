package com.dpp.order.client;

import com.dpp.order.client.dto.ProductView;
import com.dpp.order.client.dto.ReservationCommand;
import com.dpp.order.client.dto.ReservationView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    @GetMapping("/products/{id}")
    ProductView getProduct(@PathVariable("id") UUID id);

    @PostMapping("/internal/inventory/reserve")
    ReservationView reserve(@RequestBody ReservationCommand command);

    @PostMapping("/internal/inventory/reservations/{orderId}/commit")
    ReservationView commit(@PathVariable("orderId") UUID orderId);

    @PostMapping("/internal/inventory/reservations/{orderId}/release")
    ReservationView release(@PathVariable("orderId") UUID orderId);
}
