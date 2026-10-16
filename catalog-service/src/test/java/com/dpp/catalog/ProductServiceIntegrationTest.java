package com.dpp.catalog;

import com.dpp.catalog.domain.ProductStatus;
import com.dpp.catalog.service.ProductService;
import com.dpp.catalog.web.dto.ProductRequest;
import com.dpp.catalog.web.dto.ProductResponse;
import com.dpp.common.error.ApiException;
import com.dpp.common.error.ErrorCode;
import com.dpp.testing.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class ProductServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    ProductService products;

    private ProductRequest request(String sku, int totalStock) {
        return new ProductRequest(sku, "Widget " + sku, "desc", new BigDecimal("9.99"), "usd", totalStock);
    }

    private String newSku() {
        return "SKU-" + UUID.randomUUID();
    }

    @Test
    void createPersistsActiveProductWithFullAvailability() {
        ProductResponse created = products.create(request(newSku(), 10));

        assertThat(created.id()).isNotNull();
        assertThat(created.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(created.currency()).isEqualTo("USD");
        assertThat(created.totalStock()).isEqualTo(10);
        assertThat(created.reservedStock()).isZero();
        assertThat(created.availableStock()).isEqualTo(10);
    }

    @Test
    void createRejectsDuplicateSku() {
        String sku = newSku();
        products.create(request(sku, 1));

        assertThatThrownBy(() -> products.create(request(sku, 1)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        assertThatThrownBy(() -> products.get(UUID.randomUUID()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void listActiveExcludesInactiveProducts() {
        ProductResponse active = products.create(request(newSku(), 3));
        ProductResponse inactive = products.create(request(newSku(), 3));
        products.changeStatus(inactive.id(), ProductStatus.INACTIVE);

        var page = products.listActive(Pageable.unpaged());

        assertThat(page.map(ProductResponse::id)).contains(active.id());
        assertThat(page.map(ProductResponse::id)).doesNotContain(inactive.id());
    }

    @Test
    void updateChangesDetails() {
        ProductResponse created = products.create(request(newSku(), 2));

        ProductResponse updated = products.update(created.id(),
                new ProductRequest(created.sku(), "Renamed", "new desc", new BigDecimal("19.50"), "EUR", 7));

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.price()).isEqualByComparingTo("19.50");
        assertThat(updated.currency()).isEqualTo("EUR");
        assertThat(updated.totalStock()).isEqualTo(7);
    }

    @Test
    void restockIncreasesTotalStock() {
        ProductResponse created = products.create(request(newSku(), 2));

        ProductResponse restocked = products.restock(created.id(), 5);

        assertThat(restocked.totalStock()).isEqualTo(7);
        assertThat(restocked.availableStock()).isEqualTo(7);
    }
}
