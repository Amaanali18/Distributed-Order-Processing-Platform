package com.dpp.order;

import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.order.client.CatalogClient;
import com.dpp.order.client.dto.ProductView;
import com.dpp.order.client.dto.ReservationCommand;
import com.dpp.order.client.dto.ReservationView;
import com.dpp.order.domain.OrderStatus;
import com.dpp.order.service.OrderSagaService;
import com.dpp.order.service.OrderService;
import com.dpp.order.web.dto.CreateOrderRequest;
import com.dpp.order.web.dto.OrderResponse;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "dpp.outbox.initial-delay-ms=3600000"
})
class OrderServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final BigDecimal UNIT_PRICE = new BigDecimal("9.99");

    @Autowired
    OrderService orders;

    @Autowired
    OrderSagaService saga;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    CatalogClient catalog;

    private UUID stubProduct() {
        UUID productId = UUID.randomUUID();
        when(catalog.getProduct(productId)).thenReturn(new ProductView(
                productId, "SKU-" + productId, "Widget", "desc", UNIT_PRICE, "USD", "ACTIVE"));
        when(catalog.reserve(any(ReservationCommand.class))).thenReturn(new ReservationView(
                UUID.randomUUID(), UUID.randomUUID(), "RESERVED", List.of(), null));
        return productId;
    }

    private OrderResponse createOrder(UUID customerId) {
        UUID productId = stubProduct();
        return orders.create(new CreateOrderRequest(List.of(new CreateOrderRequest.Item(productId, 2))),
                customerId, "customer@dpp.local", "corr-" + UUID.randomUUID());
    }

    private int outboxCount(UUID aggregateKey, String eventType) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_messages WHERE message_key = ? AND topic = ? AND payload LIKE ?",
                Integer.class, aggregateKey.toString(), "orders.v1", "%" + eventType + "%");
        return count == null ? 0 : count;
    }

    @Test
    void createComputesTotalsAndPublishesOrderCreated() {
        UUID customerId = UUID.randomUUID();

        OrderResponse order = createOrder(customerId);

        assertThat(order.id()).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.customerId()).isEqualTo(customerId);
        assertThat(order.totalAmount()).isEqualByComparingTo("19.98");
        assertThat(order.currency()).isEqualTo("USD");
        assertThat(order.items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(2));
        assertThat(outboxCount(order.id(), "OrderCreated")).isEqualTo(1);
    }

    @Test
    void getRejectsOtherCustomers() {
        OrderResponse order = createOrder(UUID.randomUUID());

        assertThatThrownBy(() -> orders.get(order.id(), UUID.randomUUID(), false))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void cancelPendingOrderReleasesReservationAndPublishes() {
        OrderResponse order = createOrder(UUID.randomUUID());

        OrderResponse cancelled = orders.cancel(order.id(), order.customerId(), false, "corr-cancel");

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(catalog).release(order.id());
        assertThat(outboxCount(order.id(), "OrderCancelled")).isEqualTo(1);
    }

    @Test
    void cancelConfirmedOrderIsRejected() {
        OrderResponse order = createOrder(UUID.randomUUID());
        saga.onPaymentSucceeded(UUID.randomUUID(), order.id(), "corr-pay");

        assertThatThrownBy(() -> orders.cancel(order.id(), order.customerId(), false, "corr"))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }

    @Test
    void paymentSucceededConfirmsOrderAndCommitsReservation() {
        OrderResponse order = createOrder(UUID.randomUUID());

        saga.onPaymentSucceeded(UUID.randomUUID(), order.id(), "corr-pay");

        verify(catalog).commit(order.id());
        assertThat(orders.get(order.id(), order.customerId(), false).status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(outboxCount(order.id(), "OrderConfirmed")).isEqualTo(1);
    }

    @Test
    void duplicatePaymentSucceededIsIgnored() {
        OrderResponse order = createOrder(UUID.randomUUID());
        UUID eventId = UUID.randomUUID();

        saga.onPaymentSucceeded(eventId, order.id(), "corr-pay");
        saga.onPaymentSucceeded(eventId, order.id(), "corr-pay");

        assertThat(outboxCount(order.id(), "OrderConfirmed")).isEqualTo(1);
    }

    @Test
    void paymentFailedCancelsOrderAndReleasesReservation() {
        OrderResponse order = createOrder(UUID.randomUUID());

        saga.onPaymentFailed(UUID.randomUUID(), order.id(), "corr-pay");

        verify(catalog).release(order.id());
        assertThat(orders.get(order.id(), order.customerId(), false).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxCount(order.id(), "OrderCancelled")).isEqualTo(1);
    }
}
