package com.dpp.order.repository;

import com.dpp.order.domain.Order;
import com.dpp.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);

    @Query("select o.id from Order o where o.status = :status and o.createdAt < :cutoff")
    List<UUID> findIdsByStatusAndCreatedAtBefore(@Param("status") OrderStatus status,
                                                 @Param("cutoff") Instant cutoff);
}
