package com.novedadeslz.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String imageUrl;
    private List<String> imageUrls;
    private String videoUrl;
    private String category;
    private Integer stock;
    private Boolean trackInventory;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean lowStock;

    public Boolean getLowStock() {
        return Boolean.TRUE.equals(trackInventory) && stock != null && stock <= 5;
    }
}
