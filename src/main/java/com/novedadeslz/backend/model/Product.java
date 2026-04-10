package com.novedadeslz.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    private static final String IMAGE_URL_SEPARATOR = "|";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", sequenceName = "product_seq", allocationSize = 1)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "image_url", length = 500)
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private String imageUrl;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    @Builder.Default
    private Integer stock = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Business logic
    public void decreaseStock(Integer quantity) {
        if (this.stock < quantity) {
            throw new IllegalStateException(
                "Stock insuficiente. Disponible: " + this.stock + ", Solicitado: " + quantity
            );
        }
        this.stock -= quantity;
    }

    public void increaseStock(Integer quantity) {
        this.stock += quantity;
    }

    public boolean isLowStock() {
        return this.stock <= 5;
    }

    public String getImageUrl() {
        return getImageUrls().stream().findFirst().orElse(null);
    }

    public void setImageUrl(String imageUrl) {
        setImageUrls(imageUrl == null || imageUrl.isBlank() ? List.of() : List.of(imageUrl));
    }

    public List<String> getImageUrls() {
        if (imageUrl == null || imageUrl.isBlank()) {
            return List.of();
        }

        return Arrays.stream(imageUrl.split(Pattern.quote(IMAGE_URL_SEPARATOR)))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public void setImageUrls(List<String> imageUrls) {
        List<String> sanitizedImageUrls = imageUrls == null
                ? List.of()
                : imageUrls.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList();

        this.imageUrl = sanitizedImageUrls.isEmpty()
                ? null
                : sanitizedImageUrls.stream().collect(Collectors.joining(IMAGE_URL_SEPARATOR));
    }
}
