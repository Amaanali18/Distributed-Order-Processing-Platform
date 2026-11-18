package com.dpp.payment.client;

import com.dpp.payment.client.dto.OrderView;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

    @GetMapping("/{id}")
    OrderView getOrder(@PathVariable("id") java.util.UUID id);
}
