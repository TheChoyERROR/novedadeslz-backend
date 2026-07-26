package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Descuenta stock en una sola sentencia condicional.
     *
     * <p>Leer el stock y despues guardarlo no es seguro: dos pedidos simultaneos pueden leer 1
     * unidad disponible y ambos guardar 0, vendiendo dos veces la misma. Al poner la condicion
     * dentro del UPDATE, la base resuelve la carrera y devuelve 0 filas al que llego tarde.
     *
     * @return 1 si alcanzo el stock, 0 si no
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock - :quantity "
            + "WHERE p.id = :productId AND p.trackInventory = true AND p.stock >= :quantity")
    int reserveStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.stock = p.stock + :quantity "
            + "WHERE p.id = :productId AND p.trackInventory = true")
    int releaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
