package com.dpp.catalog.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RestockRequest(@NotNull @Min(1) int quantity) {
}
