package com.dpp.catalog.web;

import com.dpp.catalog.service.InventoryService;
import com.dpp.catalog.web.dto.ReservationRequest;
import com.dpp.catalog.web.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse reserve(@Valid @RequestBody ReservationRequest request) {
        return inventoryService.reserve(request);
    }

    @PostMapping("/reservations/{orderId}/commit")
    public ReservationResponse commit(@PathVariable UUID orderId) {
        return inventoryService.commit(orderId);
    }

    @PostMapping("/reservations/{orderId}/release")
    public ReservationResponse release(@PathVariable UUID orderId) {
        return inventoryService.release(orderId);
    }

    @GetMapping("/reservations/{orderId}")
    public ReservationResponse get(@PathVariable UUID orderId) {
        return inventoryService.get(orderId);
    }
}
