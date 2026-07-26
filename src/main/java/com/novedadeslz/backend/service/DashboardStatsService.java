package com.novedadeslz.backend.service;

import com.novedadeslz.backend.dto.response.DashboardStatsResponse;
import com.novedadeslz.backend.model.Order;
import com.novedadeslz.backend.repository.OrderRepository;
import com.novedadeslz.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    /** Un pedido cuenta como venta cuando ya fue confirmado. */
    private static final List<Order.OrderStatus> REVENUE_STATUSES =
            List.of(Order.OrderStatus.CONFIRMED, Order.OrderStatus.DELIVERED);

    /** Mismo umbral que usa {@code Product.isLowStock()}. */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        return DashboardStatsResponse.builder()
                .totalProducts(productRepository.countActiveProducts())
                .lowStockProducts(productRepository.countLowStockProducts(LOW_STOCK_THRESHOLD))
                .totalOrders(orderRepository.count())
                .ordersAwaitingReview(orderRepository.countByStatus(Order.OrderStatus.PAYMENT_REVIEW))
                .pendingOrders(orderRepository.countByStatus(Order.OrderStatus.PENDING))
                .totalRevenue(orderRepository.sumTotalByStatusIn(REVENUE_STATUSES))
                .revenueThisMonth(orderRepository.sumTotalByStatusInSince(REVENUE_STATUSES, startOfMonth))
                .build();
    }
}
