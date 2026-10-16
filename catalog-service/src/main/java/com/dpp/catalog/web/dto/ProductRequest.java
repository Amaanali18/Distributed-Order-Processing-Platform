package com.dpp.catalog.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(min = 3, max = 64) String sku,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @NotNull @DecimalMin(value = "0.00") BigDecimal price,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Min(0) int totalStock) {
}
