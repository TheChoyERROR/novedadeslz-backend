package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Los listados mapean los items de cada pedido, asi que sin este grafo Hibernate emite una
     * consulta extra por pedido. Con 100 pedidos por pagina eso eran ~100 viajes de ida y vuelta a
     * Oracle, que desde Render cruzan medio continente.
     */
    String ITEMS_GRAPH = "Order.items";

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByOrderNumberIgnoreCase(String orderNumber);

    @EntityGraph(value = ITEMS_GRAPH, type = EntityGraph.EntityGraphType.LOAD)
    Page<Order> findByStatus(Order.OrderStatus status, Pageable pageable);

    @EntityGraph(value = ITEMS_GRAPH, type = EntityGraph.EntityGraphType.LOAD)
    Page<Order> findByCustomerPhoneContaining(String phone, Pageable pageable);

    @EntityGraph(value = ITEMS_GRAPH, type = EntityGraph.EntityGraphType.LOAD)
    Page<Order> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Override
    @EntityGraph(value = ITEMS_GRAPH, type = EntityGraph.EntityGraphType.LOAD)
    Page<Order> findAll(Pageable pageable);

    @Override
    @EntityGraph(value = ITEMS_GRAPH, type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findById(Long id);

    boolean existsByOperationNumber(String operationNumber);

    Optional<Order> findByOperationNumber(String operationNumber);

    @Query(value = "SELECT COUNT(*) FROM orders o WHERE o.order_number LIKE :pattern", nativeQuery = true)
    Long countByOrderNumberStartingWith(@Param("pattern") String pattern);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt >= :date")
    Page<Order> findRecentOrdersByStatus(
        @Param("status") Order.OrderStatus status,
        @Param("date") LocalDateTime date,
        Pageable pageable
    );
}
