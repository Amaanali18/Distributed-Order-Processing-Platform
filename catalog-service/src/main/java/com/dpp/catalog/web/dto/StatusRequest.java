package com.dpp.catalog.web.dto;

import com.dpp.catalog.domain.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull ProductStatus status) {
}
