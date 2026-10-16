package com.dpp.catalog.service;

import com.dpp.catalog.domain.Product;
import com.dpp.catalog.domain.ProductStatus;
import com.dpp.catalog.repository.ProductRepository;
import com.dpp.catalog.web.dto.ProductRequest;
import com.dpp.catalog.web.dto.ProductResponse;
import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listActive(Pageable pageable) {
        return products.findByStatus(ProductStatus.ACTIVE, pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listAll(Pageable pageable) {
        return products.findAll(pageable).map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID id) {
        return products.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (products.existsBySku(request.sku())) {
            throw new ApiException(ErrorCode.CONFLICT, "Product SKU already exists: " + request.sku());
        }
        Product product = Product.create(
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.currency().toUpperCase(),
                request.totalStock());
        products.save(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(UUID id, ProductRequest request) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        products.findBySku(request.sku())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ApiException(ErrorCode.CONFLICT, "Product SKU already exists: " + request.sku());
                });
        product.updateDetails(
                request.name(),
                request.description(),
                request.price(),
                request.currency().toUpperCase(),
                request.totalStock());
        products.save(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse changeStatus(UUID id, ProductStatus status) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        product.changeStatus(status);
        products.save(product);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse restock(UUID id, int quantity) {
        Product product = products.findById(id)
                .orElseThrow(() -> ApiException.notFound("Product not found: " + id));
        product.addStock(quantity);
        products.save(product);
        return ProductResponse.from(product);
    }
}
