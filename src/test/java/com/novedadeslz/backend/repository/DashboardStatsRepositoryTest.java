package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Las cifras del panel se calculaban en el navegador sobre los ultimos 100 pedidos del listado, de
 * modo que a partir del pedido 101 los ingresos quedaban congelados. Estas pruebas fijan que el
 * total no dependa de cuantos pedidos quepan en una pagina.
 */
@DataJpaTest
class DashboardStatsRepositoryTest {

    private static final List<Order.OrderStatus> REVENUE_STATUSES =
            List.of(Order.OrderStatus.CONFIRMED, Order.OrderStatus.DELIVERED);

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void revenueShouldCoverEveryOrderNotJustTheFirstPage() {
        for (int i = 0; i < 105; i++) {
            orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED, new BigDecimal("10.00")));
        }

        assertEquals(0, new BigDecimal("1050.00")
                .compareTo(orderRepository.sumTotalByStatusIn(REVENUE_STATUSES)));
    }

    @Test
    void revenueShouldIgnoreOrdersThatAreNotSalesYet() {
        orderRepository.save(buildOrder(Order.OrderStatus.CONFIRMED, new BigDecimal("40.00")));
        orderRepository.save(buildOrder(Order.OrderStatus.DELIVERED, new BigDecimal("10.00")));
        // Ninguno de estos es una venta todavia.
        orderRepository.save(buildOrder(Order.OrderStatus.PENDING, new BigDecimal("999.00")));
        orderRepository.save(buildOrder(Order.OrderStatus.PAYMENT_REVIEW, new BigDecimal("999.00")));
        orderRepository.save(buildOrder(Order.OrderStatus.CANCELLED, new BigDecimal("999.00")));

        assertEquals(0, new BigDecimal("50.00")
                .compareTo(orderRepository.sumTotalByStatusIn(REVENUE_STATUSES)));
    }

    @Test
    void revenueShouldBeZeroWithoutSales() {
        assertEquals(0, BigDecimal.ZERO.compareTo(orderRepository.sumTotalByStatusIn(REVENUE_STATUSES)));
    }

    @Test
    void countByStatusShouldSeparateWhatNeedsReviewFromWhatDoesNot() {
        orderRepository.save(buildOrder(Order.OrderStatus.PAYMENT_REVIEW, BigDecimal.TEN));
        orderRepository.save(buildOrder(Order.OrderStatus.PAYMENT_REVIEW, BigDecimal.TEN));
        orderRepository.save(buildOrder(Order.OrderStatus.PENDING, BigDecimal.TEN));

        assertEquals(2, orderRepository.countByStatus(Order.OrderStatus.PAYMENT_REVIEW));
        assertEquals(1, orderRepository.countByStatus(Order.OrderStatus.PENDING));
    }

    private Order buildOrder(Order.OrderStatus status, BigDecimal total) {
        return Order.builder()
                .orderNumber("ORD-" + UUID.randomUUID())
                .publicToken(UUID.randomUUID().toString())
                .customerName("Cliente Prueba")
                .customerPhone("+51987654321")
                .total(total)
                .status(status)
                .paymentMethod("yape")
                .whatsappSent(false)
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();
    }
}
