package com.dpp.order.service;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.order.client.CatalogClient;
import com.dpp.order.client.dto.ProductView;
import com.dpp.order.client.dto.ReservationCommand;
import com.dpp.order.domain.Order;
import com.dpp.order.domain.OrderStatus;
import com.dpp.order.messaging.OrderEventPublisher;
import com.dpp.order.repository.OrderRepository;
import com.dpp.order.web.dto.CreateOrderRequest;
import com.dpp.order.web.dto.OrderResponse;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CatalogClient catalog;
    private final OrderEventPublisher publisher;

    public OrderService(OrderRepository orders, CatalogClient catalog, OrderEventPublisher publisher) {
        this.orders = orders;
        this.catalog = catalog;
        this.publisher = publisher;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, UUID customerId, String customerEmail, String correlationId) {
        List<Order.OrderItem> items = new ArrayList<>();
        List<ReservationCommand.Item> reservationItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        String currency = null;

        for (CreateOrderRequest.Item requested : request.items()) {
            ProductView product = fetchProduct(requested.productId());
            if (!"ACTIVE".equals(product.status())) {
                throw ApiException.notFound("Product is not available: " + requested.productId());
            }
            if (currency == null) {
                currency = product.currency();
            } else if (!currency.equalsIgnoreCase(product.currency())) {
                throw new ApiException(ErrorCode.CONFLICT, "Mixed currencies are not supported in a single order");
            }
            items.add(new Order.OrderItem(product.id(), product.sku(), product.name(),
                    requested.quantity(), product.price()));
            reservationItems.add(new ReservationCommand.Item(product.id(), requested.quantity()));
            total = total.add(product.price().multiply(BigDecimal.valueOf(requested.quantity())));
        }

        UUID orderId = UUID.randomUUID();
        reserve(orderId, reservationItems);

        Order order = Order.pending(orderId, customerId, customerEmail, total, currency, items);
        orders.save(order);
        publisher.orderCreated(order, correlationId);
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listMine(UUID customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listAll(OrderStatus status, Pageable pageable) {
        return (status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable))
                .map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id, UUID customerId, boolean admin) {
        Order order = requireOrder(id);
        authorize(order, customerId, admin);
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(UUID id, UUID customerId, boolean admin, String correlationId) {
        Order order = requireOrder(id);
        authorize(order, customerId, admin);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only pending orders can be cancelled, current status is " + order.getStatus());
        }

        order.cancel();
        orders.save(order);
        releaseQuietly(order.getId());
        publisher.orderCancelled(order, correlationId, "cancelled before payment");
        return OrderResponse.from(order);
    }

    private ProductView fetchProduct(UUID productId) {
        try {
            return catalog.getProduct(productId);
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound("Product not found: " + productId);
        } catch (FeignException e) {
            log.warn("Catalog lookup failed for product {}: {}", productId, e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Catalog service is unavailable");
        }
    }

    private void reserve(UUID orderId, List<ReservationCommand.Item> items) {
        try {
            catalog.reserve(new ReservationCommand(orderId, items, null));
        } catch (FeignException.Conflict e) {
            throw new ApiException(ErrorCode.INSUFFICIENT_STOCK, "Requested quantity is not available");
        } catch (FeignException.NotFound e) {
            throw ApiException.notFound("One or more products are not available");
        } catch (FeignException e) {
            log.warn("Stock reservation failed for order {}: {}", orderId, e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Catalog service is unavailable");
        }
    }

    private void releaseQuietly(UUID orderId) {
        try {
            catalog.release(orderId);
        } catch (FeignException e) {
            log.warn("Reservation release failed for order {}: {}", orderId, e.getMessage());
        }
    }

    private Order requireOrder(UUID id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found: " + id));
    }

    private void authorize(Order order, UUID customerId, boolean admin) {
        if (!admin && !order.getCustomerId().equals(customerId)) {
            throw ApiException.forbidden("Order does not belong to the current user");
        }
    }
}
