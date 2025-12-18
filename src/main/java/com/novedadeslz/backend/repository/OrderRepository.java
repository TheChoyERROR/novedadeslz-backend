package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByStatus(Order.OrderStatus status, Pageable pageable);

    Page<Order> findByCustomerPhoneContaining(String phone, Pageable pageable);

    Page<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    boolean existsByOperationNumber(String operationNumber);

    Optional<Order> findByOperationNumber(String operationNumber);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderNumber LIKE :prefix%")
    Long countByOrderNumberStartingWith(@Param("prefix") String prefix);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt >= :date")
    Page<Order> findRecentOrdersByStatus(
        @Param("status") Order.OrderStatus status,
        @Param("date") LocalDateTime date,
        Pageable pageable
    );
}
