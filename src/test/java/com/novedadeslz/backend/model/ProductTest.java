package com.novedadeslz.backend.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProductTest {

    @Test
    void imageUrlSetterShouldKeepBackwardCompatibility() {
        Product product = new Product();

        product.setImageUrl("https://cdn.example.com/producto-1.jpg");

        assertEquals("https://cdn.example.com/producto-1.jpg", product.getImageUrl());
        assertIterableEquals(
                List.of("https://cdn.example.com/producto-1.jpg"),
                product.getImageUrls()
        );
    }

    @Test
    void imageUrlsSetterShouldExposeCoverAndGallery() {
        Product product = new Product();

        product.setImageUrls(List.of(
                "https://cdn.example.com/producto-1.jpg",
                "https://cdn.example.com/producto-2.jpg",
                "https://cdn.example.com/producto-3.jpg"
        ));

        assertEquals("https://cdn.example.com/producto-1.jpg", product.getImageUrl());
        assertIterableEquals(
                List.of(
                        "https://cdn.example.com/producto-1.jpg",
                        "https://cdn.example.com/producto-2.jpg",
                        "https://cdn.example.com/producto-3.jpg"
                ),
                product.getImageUrls()
        );
    }

    @Test
    void emptyGalleryShouldClearStoredImageData() {
        Product product = new Product();

        product.setImageUrls(List.of());

        assertNull(product.getImageUrl());
        assertIterableEquals(List.of(), product.getImageUrls());
    }

    @Test
    void imageUrlsSetterShouldSupportLargeGallery() {
        Product product = new Product();
        List<String> imageUrls = IntStream.rangeClosed(1, 20)
                .mapToObj(index -> "https://res.cloudinary.com/demo/image/upload/v1/novedadeslz/products/" + index + ".jpg")
                .toList();

        product.setImageUrls(imageUrls);

        assertEquals(imageUrls.getFirst(), product.getImageUrl());
        assertIterableEquals(imageUrls, product.getImageUrls());
    }

    @Test
    void productWithoutInventoryTrackingShouldAlwaysBeAvailable() {
        Product product = Product.builder()
                .stock(0)
                .trackInventory(false)
                .build();

        assertTrue(product.hasAvailableStock(5));
        assertFalse(product.isOutOfStock());
        assertFalse(product.isLowStock());
    }
}
