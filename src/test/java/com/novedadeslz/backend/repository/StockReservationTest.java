package com.novedadeslz.backend.repository;

import com.novedadeslz.backend.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * El stock se aparta con una sentencia condicional en la base.
 *
 * <p>Comprobar el stock y despues guardarlo no sirve: dos pedidos simultaneos leen la misma unidad
 * disponible y ambos la venden. La condicion tiene que vivir dentro del UPDATE.
 */
@DataJpaTest
@DirtiesContext
class StockReservationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void reserveStockShouldSucceedWhileThereAreUnits() {
        Product product = productRepository.save(buildProduct(2, true));

        assertEquals(1, productRepository.reserveStock(product.getId(), 1));
        assertEquals(1, productRepository.reserveStock(product.getId(), 1));

        // La tercera ya no tiene de donde salir.
        assertEquals(0, productRepository.reserveStock(product.getId(), 1));
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void reserveStockShouldRejectAnAmountBiggerThanTheStock() {
        Product product = productRepository.save(buildProduct(2, true));

        assertEquals(0, productRepository.reserveStock(product.getId(), 3));
        // Un intento fallido no debe dejar el stock en negativo ni alterarlo.
        assertEquals(2, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void reserveStockShouldIgnoreProductsWithoutInventoryTracking() {
        Product product = productRepository.save(buildProduct(0, false));

        // Devuelve 0 filas: el servicio ni siquiera lo intenta para estos productos.
        assertEquals(0, productRepository.reserveStock(product.getId(), 5));
        assertEquals(0, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void releaseStockShouldReturnTheUnitsToInventory() {
        Product product = productRepository.save(buildProduct(3, true));
        productRepository.reserveStock(product.getId(), 2);

        productRepository.releaseStock(product.getId(), 2);

        assertEquals(3, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    private Product buildProduct(int stock, boolean trackInventory) {
        return Product.builder()
                .name("Producto")
                .price(new BigDecimal("10.00"))
                .stock(stock)
                .trackInventory(trackInventory)
                .active(true)
                .build();
    }
}
