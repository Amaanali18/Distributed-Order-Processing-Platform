package com.dpp.catalog;

import com.dpp.catalog.domain.ProductStatus;
import com.dpp.catalog.service.InventoryService;
import com.dpp.catalog.service.ProductService;
import com.dpp.catalog.web.dto.ProductRequest;
import com.dpp.catalog.web.dto.ProductResponse;
import com.dpp.catalog.web.dto.ReservationRequest;
import com.dpp.catalog.web.dto.ReservationResponse;
import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class InventoryServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    ProductService products;

    @Autowired
    InventoryService inventory;

    private ProductResponse newProduct(int totalStock) {
        String sku = "SKU-" + UUID.randomUUID();
        return products.create(new ProductRequest(sku, "Widget", "desc", new BigDecimal("5.00"), "USD", totalStock));
    }

    private ReservationRequest reserveRequest(UUID orderId, UUID productId, int quantity) {
        return new ReservationRequest(orderId, List.of(new ReservationRequest.Item(productId, quantity)), null);
    }

    @Test
    void reserveMovesStockFromAvailableToReserved() {
        ProductResponse product = newProduct(5);

        ReservationResponse reservation = inventory.reserve(
                reserveRequest(UUID.randomUUID(), product.id(), 2));

        assertThat(reservation.status()).isEqualTo(com.dpp.catalog.domain.ReservationStatus.HELD);
        ProductResponse after = products.get(product.id());
        assertThat(after.reservedStock()).isEqualTo(2);
        assertThat(after.availableStock()).isEqualTo(3);
    }

    @Test
    void reserveIsIdempotentForTheSameOrder() {
        ProductResponse product = newProduct(5);
        UUID orderId = UUID.randomUUID();

        ReservationResponse first = inventory.reserve(reserveRequest(orderId, product.id(), 2));
        ReservationResponse second = inventory.reserve(reserveRequest(orderId, product.id(), 2));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(products.get(product.id()).reservedStock()).isEqualTo(2);
    }

    @Test
    void reserveFailsWhenStockIsInsufficient() {
        ProductResponse product = newProduct(1);

        assertThatThrownBy(() -> inventory.reserve(reserveRequest(UUID.randomUUID(), product.id(), 2)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
        assertThat(products.get(product.id()).reservedStock()).isZero();
    }

    @Test
    void reserveRejectsInactiveProduct() {
        ProductResponse product = newProduct(5);
        products.changeStatus(product.id(), ProductStatus.INACTIVE);

        assertThatThrownBy(() -> inventory.reserve(reserveRequest(UUID.randomUUID(), product.id(), 1)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    @Test
    void commitConsumesReservedStock() {
        ProductResponse product = newProduct(5);
        UUID orderId = UUID.randomUUID();
        inventory.reserve(reserveRequest(orderId, product.id(), 2));

        ReservationResponse committed = inventory.commit(orderId);

        assertThat(committed.status()).isEqualTo(com.dpp.catalog.domain.ReservationStatus.COMMITTED);
        ProductResponse after = products.get(product.id());
        assertThat(after.totalStock()).isEqualTo(3);
        assertThat(after.reservedStock()).isZero();
        assertThat(after.availableStock()).isEqualTo(3);
    }

    @Test
    void releaseReturnsStockToAvailable() {
        ProductResponse product = newProduct(5);
        UUID orderId = UUID.randomUUID();
        inventory.reserve(reserveRequest(orderId, product.id(), 2));

        ReservationResponse released = inventory.release(orderId);

        assertThat(released.status()).isEqualTo(com.dpp.catalog.domain.ReservationStatus.RELEASED);
        ProductResponse after = products.get(product.id());
        assertThat(after.totalStock()).isEqualTo(5);
        assertThat(after.reservedStock()).isZero();
        assertThat(after.availableStock()).isEqualTo(5);
    }

    @Test
    void commitAfterReleaseIsRejected() {
        ProductResponse product = newProduct(5);
        UUID orderId = UUID.randomUUID();
        inventory.reserve(reserveRequest(orderId, product.id(), 1));
        inventory.release(orderId);

        assertThatThrownBy(() -> inventory.commit(orderId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVALID_STATE_TRANSITION));
    }
}
