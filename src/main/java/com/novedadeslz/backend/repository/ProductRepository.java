package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryAndActiveTrue(String category, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Product> searchProducts(@Param("search") String search, Pageable pageable);

    List<Product> findByStockLessThanEqualAndActiveTrue(Integer threshold);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true")
    Long countActiveProducts();

    /** Productos activos que llevan control de inventario y ya estan por agotarse. */
    @Query("SELECT COUNT(p) FROM Product p "
            + "WHERE p.active = true AND p.trackInventory = true AND p.stock <= :threshold")
    long countLowStockProducts(@Param("threshold") Integer threshold);

    @Query("SELECT SUM(p.price * p.stock) FROM Product p WHERE p.active = true")
    Double getTotalInventoryValue();
}
