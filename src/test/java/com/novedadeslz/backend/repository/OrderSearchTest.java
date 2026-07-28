package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El panel busca pedidos por lo que la clienta tiene a mano: el numero que le dimos, su nombre o
 * su telefono. Antes solo se podia por telefono, y filtrar por estado anulaba la busqueda.
 */
@DataJpaTest
class OrderSearchTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void seed() {
        orderRepository.save(build("ORD-20260725-0001", "Maria Lopez", "+51987111222",
                Order.OrderStatus.PAYMENT_REVIEW));
        orderRepository.save(build("ORD-20260725-0002", "Rosa Diaz", "+51987333444",
                Order.OrderStatus.CONFIRMED));
        orderRepository.save(build("ORD-20260726-0001", "Maria Quispe", "+51987555666",
                Order.OrderStatus.PAYMENT_REVIEW));
    }

    @Test
    void shouldFindByOrderNumber() {
        var found = orderRepository.searchOrders("20260725-0002", PageRequest.of(0, 10));

        assertEquals(1, found.getTotalElements());
        assertEquals("Rosa Diaz", found.getContent().get(0).getCustomerName());
    }

    @Test
    void shouldFindByCustomerNameIgnoringCase() {
        var found = orderRepository.searchOrders("maria", PageRequest.of(0, 10));

        assertEquals(2, found.getTotalElements());
    }

    @Test
    void shouldFindByPartialPhone() {
        var found = orderRepository.searchOrders("333444", PageRequest.of(0, 10));

        assertEquals(1, found.getTotalElements());
    }

    @Test
    void shouldCombineStatusWithSearch() {
        // Antes esto era imposible: elegir un estado descartaba el texto buscado.
        var found = orderRepository.searchOrdersByStatus(
                Order.OrderStatus.PAYMENT_REVIEW, "maria", PageRequest.of(0, 10));

        assertEquals(2, found.getTotalElements());
        assertTrue(found.getContent().stream()
                .allMatch(o -> o.getStatus() == Order.OrderStatus.PAYMENT_REVIEW));
    }

    @Test
    void shouldReturnNothingWhenThereIsNoMatch() {
        assertEquals(0, orderRepository.searchOrders("no-existe", PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void shouldPaginate() {
        var firstPage = orderRepository.searchOrders("ORD-", PageRequest.of(0, 2));

        assertEquals(3, firstPage.getTotalElements());
        assertEquals(2, firstPage.getContent().size());
        assertEquals(2, firstPage.getTotalPages());
    }

    private Order build(String number, String name, String phone, Order.OrderStatus status) {
        return Order.builder()
                .orderNumber(number)
                .publicToken(UUID.randomUUID().toString())
                .customerName(name)
                .customerPhone(phone)
                .total(new BigDecimal("10.00"))
                .status(status)
                .paymentMethod("yape")
                .whatsappSent(false)
                .items(new ArrayList<>())
                .build();
    }
}
