package com.dpp.catalog.repository;

import com.dpp.catalog.domain.Product;
import com.dpp.catalog.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByStatus(ProductStatus status, Pageable pageable);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Product p
            SET p.reservedStock = p.reservedStock + :quantity, p.updatedAt = :now
            WHERE p.id = :id
              AND p.status = :active
              AND (p.totalStock - p.reservedStock) >= :quantity
            """)
    int reserveStock(@Param("id") UUID id,
                     @Param("quantity") int quantity,
                     @Param("active") ProductStatus active,
                     @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Product p
            SET p.reservedStock = p.reservedStock - :quantity, p.updatedAt = :now
            WHERE p.id = :id
              AND p.reservedStock >= :quantity
            """)
    int releaseStock(@Param("id") UUID id,
                     @Param("quantity") int quantity,
                     @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Product p
            SET p.reservedStock = p.reservedStock - :quantity,
                p.totalStock = p.totalStock - :quantity,
                p.updatedAt = :now
            WHERE p.id = :id
              AND p.reservedStock >= :quantity
              AND p.totalStock >= :quantity
            """)
    int commitStock(@Param("id") UUID id,
                    @Param("quantity") int quantity,
                    @Param("now") Instant now);
}
