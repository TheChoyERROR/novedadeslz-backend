package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void countByOrderNumberStartingWithShouldWorkInH2() {
        orderRepository.save(buildOrder("ORD-20260409-0001"));
        orderRepository.save(buildOrder("ORD-20260409-0002"));
        orderRepository.save(buildOrder("ORD-20260408-0001"));

        long count = orderRepository.countByOrderNumberStartingWith("ORD-20260409%");

        assertEquals(2L, count);
    }

    private Order buildOrder(String orderNumber) {
        return Order.builder()
                .orderNumber(orderNumber)
                .customerName("Cliente Demo")
                .customerPhone("987654321")
                .customerEmail("cliente@example.com")
                .customerAddress("Av. Demo 123")
                .customerCity("Lima")
                .paymentMethod("yape")
                .total(new BigDecimal("10.00"))
                .build();
    }
}
