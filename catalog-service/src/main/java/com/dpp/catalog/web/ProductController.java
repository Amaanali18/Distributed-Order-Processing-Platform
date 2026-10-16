package com.dpp.catalog.web;

import com.dpp.catalog.domain.ProductStatus;
import com.dpp.catalog.service.ProductService;
import com.dpp.catalog.web.dto.ProductResponse;
import com.dpp.common.error.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Page<ProductResponse> list(Pageable pageable) {
        return productService.listActive(pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        ProductResponse product = productService.get(id);
        if (product.status() == ProductStatus.INACTIVE) {
            throw ApiException.notFound("Product not found: " + id);
        }
        return product;
    }
}
