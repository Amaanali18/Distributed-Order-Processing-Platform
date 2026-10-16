package com.dpp.catalog.web;

import com.dpp.catalog.domain.ProductStatus;
import com.dpp.catalog.service.ProductService;
import com.dpp.catalog.web.dto.ProductRequest;
import com.dpp.catalog.web.dto.ProductResponse;
import com.dpp.catalog.web.dto.RestockRequest;
import com.dpp.catalog.web.dto.StatusRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/products")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Page<ProductResponse> list(Pageable pageable) {
        return productService.listAll(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public ProductResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return productService.changeStatus(id, request.status());
    }

    @PostMapping("/{id}/restock")
    public ProductResponse restock(@PathVariable UUID id, @Valid @RequestBody RestockRequest request) {
        return productService.restock(id, request.quantity());
    }
}
